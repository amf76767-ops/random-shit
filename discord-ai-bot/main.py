import asyncio
import base64
import io
import json
import os
import re
import time
from collections import OrderedDict

import aiohttp
import discord
from discord import app_commands

HERE = os.path.dirname(os.path.abspath(__file__))

REACT_HINT = (" Wenn es passt, darfst du ganz am Anfang deiner Antwort genau eine Reaktion setzen, "
              "im Format [react:EMOJI] mit einem normalen Emoji, z. B. [react:😂]. Sonst lass es weg.")


def load_config():
    cfg = {}
    path = os.path.join(HERE, "config.json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            cfg = json.load(f)
    cfg["discord_token"] = os.environ.get("DISCORD_TOKEN") or cfg.get("discord_token", "")
    cfg["mistral_api_key"] = os.environ.get("MISTRAL_API_KEY") or cfg.get("mistral_api_key", "")
    cfg.setdefault("model", "mistral-small-latest")
    cfg.setdefault("fallback_models", ["ministral-8b-latest", "ministral-3b-latest"])
    cfg.setdefault("vision_models", ["mistral-small-latest", "pixtral-12b-latest"])
    cfg.setdefault("image_agent_model", "mistral-medium-latest")
    cfg.setdefault("system_prompt", "Du bist ein hilfreicher Assistent in einem Discord-Server. Antworte kurz und in der Sprache der Frage.")
    cfg.setdefault("memory_messages", 6)
    cfg.setdefault("cooldown_seconds", 5)
    cfg.setdefault("image_cooldown_seconds", 30)
    cfg.setdefault("max_tokens", 800)
    cfg.setdefault("stream", True)
    cfg.setdefault("answer_mentions", True)
    cfg.setdefault("answer_replies", True)
    cfg.setdefault("answer_dms", True)
    cfg.setdefault("bot_reactions", True)
    cfg.setdefault("reaction_buttons", True)
    return cfg


CFG = load_config()
if not CFG["discord_token"] or not CFG["mistral_api_key"]:
    raise SystemExit("Bitte discord_token und mistral_api_key in config.json eintragen.")

API = "https://api.mistral.ai/v1"
DATA_PATH = os.path.join(HERE, "data.json")
NO_PINGS = discord.AllowedMentions.none()
REACT_RE = re.compile(r"^\s*\[react:\s*([^\]\s]{1,16})\s*\]\s*")
HEADER_RE = re.compile(r"^\*\*[^*\n]{1,100}:\*\* [^\n]*\n\n")
REGENERATE, MORE, DELETE = "🔄", "➕", "❌"
THINKING = "👀"
MAX_IMAGES = 3
MAX_IMAGE_BYTES = 8 * 1024 * 1024

last_use = {}
api_lock = asyncio.Lock()
last_call = [0.0]
answers = OrderedDict()
DATA = {"memory": {}, "answers": [], "personas": {}, "image_agent": None}


def load_data():
    if not os.path.exists(DATA_PATH):
        return
    try:
        with open(DATA_PATH, encoding="utf-8") as f:
            saved = json.load(f)
    except Exception as e:
        print(f"[Daten] data.json konnte nicht gelesen werden ({e}), starte leer.")
        return
    DATA["memory"] = saved.get("memory", {})
    DATA["personas"] = saved.get("personas", {})
    DATA["image_agent"] = saved.get("image_agent")
    for entry in saved.get("answers", []):
        for mid in entry.get("ids", []):
            answers[int(mid)] = entry


_save_task = [None]


def write_data():
    unique, seen = [], set()
    for entry in answers.values():
        if id(entry) not in seen:
            seen.add(id(entry))
            unique.append(entry)
    out = {"memory": DATA["memory"], "personas": DATA["personas"], "image_agent": DATA["image_agent"], "answers": unique}
    tmp = DATA_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False)
    os.replace(tmp, DATA_PATH)


def save_soon():
    if _save_task[0] and not _save_task[0].done():
        return

    async def later():
        await asyncio.sleep(3)
        try:
            write_data()
        except Exception as e:
            print(f"[Daten] Speichern fehlgeschlagen: {e}")

    _save_task[0] = asyncio.get_running_loop().create_task(later())


def user_memory(user_id):
    return DATA["memory"].setdefault(str(user_id), [])


def push_memory(user_id, question, answer):
    mem = user_memory(user_id)
    mem += [{"role": "user", "content": question}, {"role": "assistant", "content": answer}]
    keep = max(0, int(CFG["memory_messages"])) * 2
    del mem[:max(0, len(mem) - keep)]
    save_soon()


def remember_answer(message_ids, asker_id, guild_id, history, question, answer):
    entry = {"asker": asker_id, "guild": guild_id, "history": history, "question": question, "answer": answer, "ids": list(message_ids)}
    for mid in message_ids:
        answers[mid] = entry
        answers.move_to_end(mid)
    while len(answers) > 1000:
        answers.popitem(last=False)
    save_soon()


class Bot(discord.Client):
    def __init__(self):
        intents = discord.Intents.default()
        intents.message_content = True
        super().__init__(intents=intents)
        self.tree = app_commands.CommandTree(self)
        self.http_session = None

    async def setup_hook(self):
        self.http_session = aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=120))
        await self.tree.sync()

    async def close(self):
        try:
            write_data()
        except Exception:
            pass
        if self.http_session:
            await self.http_session.close()
        await super().close()


bot = Bot()


def headers():
    return {"Authorization": "Bearer " + CFG["mistral_api_key"], "Content-Type": "application/json"}


def mistral_error(status, text):
    try:
        data = json.loads(text)
        msg = data.get("message") or data.get("detail") or data.get("error") or text
        if isinstance(msg, (dict, list)):
            msg = json.dumps(msg)
    except Exception:
        msg = text
    return f"{status}: {str(msg)[:200]}"


async def pace():
    wait = 1.1 - (time.monotonic() - last_call[0])
    if wait > 0:
        await asyncio.sleep(wait)
    last_call[0] = time.monotonic()


async def call_model(model, messages, on_text=None):
    stream = bool(on_text) and CFG["stream"]
    payload = {"model": model, "messages": messages, "max_tokens": int(CFG["max_tokens"]), "stream": stream}
    waits = [2, 5, 10]
    for attempt in range(len(waits) + 1):
        await pace()
        async with bot.http_session.post(API + "/chat/completions", json=payload, headers=headers()) as r:
            if r.status == 200 and stream:
                full = ""
                async for raw in r.content:
                    line = raw.decode("utf-8", "ignore").strip()
                    if not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if data == "[DONE]":
                        break
                    try:
                        delta = json.loads(data)["choices"][0]["delta"].get("content")
                    except (ValueError, KeyError, IndexError):
                        continue
                    if isinstance(delta, list):
                        delta = "".join(c.get("text", "") for c in delta if isinstance(c, dict))
                    if delta:
                        full += delta
                        await on_text(full)
                return full.strip()
            text = await r.text()
            if r.status == 200:
                content = json.loads(text)["choices"][0]["message"]["content"]
                if isinstance(content, list):
                    content = "".join(c.get("text", "") for c in content if isinstance(c, dict))
                return content.strip()
            err = mistral_error(r.status, text)
            print(f"[Mistral] {model} -> {err}")
            if r.status in (401, 403):
                raise PermissionError("Der Mistral API-Key ist falsch oder dein Mistral-Konto hat noch keinen Plan (console.mistral.ai -> Billing/Plan aktivieren).")
            if r.status in (429, 500, 502, 503) and attempt < len(waits):
                retry = r.headers.get("Retry-After")
                await asyncio.sleep(min(float(retry), 20) if retry else waits[attempt])
                continue
            raise RuntimeError(err)
    raise RuntimeError("keine Antwort")


def system_prompt(guild_id):
    text = CFG["system_prompt"]
    persona = DATA["personas"].get(str(guild_id)) if guild_id else None
    if persona:
        text += " Deine Persönlichkeit und Art zu reden auf diesem Server: " + persona
    if CFG["bot_reactions"]:
        text += REACT_HINT
    return text


def visible(text):
    if text.lstrip().startswith("[") and "]" not in text[:24]:
        return ""
    return REACT_RE.sub("", text, count=1)


async def generate(history, question, guild_id=None, images=None, on_text=None):
    content = question
    if images:
        content = [{"type": "text", "text": question or "Was ist auf dem Bild?"}]
        content += [{"type": "image_url", "image_url": url} for url in images]
        models = list(CFG["vision_models"])
    else:
        models = [CFG["model"]] + [m for m in CFG["fallback_models"] if m != CFG["model"]]
    messages = [{"role": "system", "content": system_prompt(guild_id)}] + list(history) + [{"role": "user", "content": content}]
    errors = []
    async with api_lock:
        for model in models:
            try:
                raw = await call_model(model, messages, on_text)
                break
            except PermissionError:
                raise
            except Exception as e:
                errors.append(f"{model} ({e})")
        else:
            raise RuntimeError("Mistral hat abgelehnt: " + "; ".join(errors))
    emoji = None
    m = REACT_RE.match(raw)
    if m:
        emoji = m.group(1)
        raw = raw[m.end():]
    return raw.strip() or "(leere Antwort)", emoji


async def image_inputs(attachments):
    urls = []
    for a in attachments:
        if len(urls) >= MAX_IMAGES:
            break
        if not (a.content_type or "").startswith("image/") or a.size > MAX_IMAGE_BYTES:
            continue
        data = await a.read()
        urls.append(f"data:{a.content_type.split(';')[0]};base64,{base64.b64encode(data).decode()}")
    return urls


def chunks(text, size=1900):
    parts = []
    while len(text) > size:
        cut = text.rfind("\n", 0, size)
        if cut < size // 2:
            cut = size
        parts.append(text[:cut])
        text = text[cut:].lstrip("\n")
    parts.append(text)
    return parts


class Live:
    def __init__(self, first_send, more_send, prefix=""):
        self.first_send = first_send
        self.more_send = more_send
        self.prefix = prefix
        self.msgs = []
        self.shown = []
        self.last = 0.0
        self.lock = asyncio.Lock()

    def adopt(self, message):
        self.msgs = [message]
        self.shown = [message.content]

    async def update(self, text, final=False):
        if not final and time.monotonic() - self.last < 1.2:
            return
        body = text if final else visible(text)
        if not body.strip():
            return
        async with self.lock:
            self.last = time.monotonic()
            parts = chunks(self.prefix + body)
            if not final:
                parts[-1] += " ▌"
            for i, p in enumerate(parts):
                if i == 0 and not self.msgs:
                    self.msgs.append(await self.first_send(p))
                    self.shown.append(p)
                elif i < len(self.msgs):
                    if self.shown[i] != p:
                        try:
                            await self.msgs[i].edit(content=p, allowed_mentions=NO_PINGS)
                            self.shown[i] = p
                        except discord.HTTPException:
                            pass
                else:
                    self.msgs.append(await self.more_send(p))
                    self.shown.append(p)
            while final and len(self.msgs) > len(parts):
                extra = self.msgs.pop()
                self.shown.pop()
                try:
                    await extra.delete()
                except discord.HTTPException:
                    pass

    async def fail(self, error):
        async with self.lock:
            text = self.prefix + f"Fehler: {error}"
            if self.msgs:
                try:
                    await self.msgs[0].edit(content=text[:1990], allowed_mentions=NO_PINGS)
                except discord.HTTPException:
                    pass
            else:
                await self.first_send(text[:1990])


async def answer_live(live, history, question, guild_id, images=None, thinking_on=None):
    if thinking_on is not None:
        await add_status(thinking_on, THINKING)
    try:
        answer, emoji = await generate(history, question, guild_id, images, on_text=live.update)
    except Exception as e:
        await live.fail(e)
        return None, None
    finally:
        if thinking_on is not None:
            await remove_status(thinking_on, THINKING)
    await live.update(answer, final=True)
    return answer, emoji


async def add_status(message, emoji):
    try:
        await message.add_reaction(emoji)
    except discord.HTTPException:
        pass


async def remove_status(message, emoji):
    try:
        await message.remove_reaction(emoji, bot.user)
    except discord.HTTPException:
        pass


def cooldown_left(key, seconds):
    now = time.monotonic()
    left = seconds - (now - last_use.get(key, -seconds))
    if left <= 0:
        last_use[key] = now
    return left


async def add_reaction(message, emoji):
    if not emoji or not CFG["bot_reactions"]:
        return
    try:
        await message.add_reaction(emoji)
    except discord.HTTPException:
        pass


async def add_buttons(messages):
    if not CFG["reaction_buttons"] or not messages:
        return
    for e in (REGENERATE, MORE, DELETE):
        try:
            await messages[-1].add_reaction(e)
        except discord.HTTPException:
            return


def clean_question(message):
    text = message.content
    if bot.user:
        text = re.sub(rf"<@!?{bot.user.id}>", "", text)
    return text.strip()


def strip_header(text):
    return HEADER_RE.sub("", text, count=1)


def with_image_note(question, images):
    if not images:
        return question
    return (question + " " if question else "") + f"[{len(images)} Bild(er) angehängt]"


async def reply_chain(message):
    history = []
    ref = message.reference
    steps = 0
    limit = max(0, int(CFG["memory_messages"]))
    while ref and ref.message_id and steps < limit:
        steps += 1
        prev = ref.resolved if isinstance(ref.resolved, discord.Message) else None
        if prev is None:
            try:
                prev = await message.channel.fetch_message(ref.message_id)
            except discord.HTTPException:
                break
        stored = answers.get(prev.id)
        if prev.author == bot.user and stored:
            history[0:0] = stored["history"] + [{"role": "user", "content": stored["question"]}, {"role": "assistant", "content": stored["answer"]}]
            break
        role = "assistant" if prev.author == bot.user else "user"
        content = strip_header(prev.content) if role == "assistant" else clean_question(prev)
        if content:
            history.insert(0, {"role": role, "content": content})
        ref = prev.reference
    return history[-limit * 2:] if limit else []


@bot.tree.command(name="ai", description="Frag die KI (Mistral), optional mit Bild")
@app_commands.describe(nachricht="Deine Frage oder Nachricht an die KI", bild="Optional: ein Bild, das die KI anschauen soll")
async def ai(interaction: discord.Interaction, nachricht: str, bild: discord.Attachment = None):
    left = cooldown_left(interaction.user.id, float(CFG["cooldown_seconds"]))
    if left > 0:
        await interaction.response.send_message(f"Warte noch {left:.0f} Sekunden.", ephemeral=True)
        return
    await interaction.response.defer(thinking=True)
    question = nachricht[:4000]
    images = await image_inputs([bild]) if bild else []
    if bild and not images:
        await interaction.followup.send("Das Bild ist zu groß (max. 8 MB) oder kein Bild.", ephemeral=True)
        return
    history = list(user_memory(interaction.user.id))
    gid = interaction.guild_id
    header = f"**{interaction.user.display_name}:** {nachricht[:300]}" + (" 🖼️" if images else "") + "\n\n"
    live = Live(lambda p: interaction.followup.send(p, allowed_mentions=NO_PINGS, wait=True),
                lambda p: interaction.followup.send(p, allowed_mentions=NO_PINGS, wait=True), header)
    answer, emoji = await answer_live(live, history, question, gid, images)
    if answer is None:
        return
    stored_q = with_image_note(question, images)
    push_memory(interaction.user.id, stored_q, answer)
    remember_answer([m.id for m in live.msgs], interaction.user.id, gid, history, stored_q, answer)
    await add_reaction(live.msgs[0], emoji)
    await add_buttons(live.msgs)


@bot.tree.command(name="ai-reset", description="Die KI vergisst euer bisheriges Gespräch")
async def ai_reset(interaction: discord.Interaction):
    DATA["memory"].pop(str(interaction.user.id), None)
    save_soon()
    await interaction.response.send_message("Gespräch vergessen.", ephemeral=True)


@bot.tree.command(name="zusammenfassen", description="Fasst die letzten Nachrichten in diesem Channel zusammen")
@app_commands.describe(anzahl="Wie viele Nachrichten (10 bis 200, Standard 50)")
async def zusammenfassen(interaction: discord.Interaction, anzahl: app_commands.Range[int, 10, 200] = 50):
    left = cooldown_left(("sum", interaction.user.id), 20.0)
    if left > 0:
        await interaction.response.send_message(f"Warte noch {left:.0f} Sekunden.", ephemeral=True)
        return
    await interaction.response.defer(thinking=True)
    lines = []
    try:
        async for m in interaction.channel.history(limit=anzahl):
            text = strip_header(m.content).replace("\n", " ").strip()
            if m.attachments:
                text += " [Anhang]"
            if text:
                lines.append(f"{m.author.display_name}: {text[:400]}")
    except discord.HTTPException as e:
        await interaction.followup.send(f"Ich kann den Verlauf hier nicht lesen ({e.status}). Fehlt mir 'Read Message History'?")
        return
    if not lines:
        await interaction.followup.send("Hier gibt es nichts zum Zusammenfassen.")
        return
    total, picked = 0, []
    for line in lines:
        if total + len(line) > 12000:
            break
        picked.append(line)
        total += len(line)
    picked.reverse()
    prompt = ("Fasse dieses Discord-Gespräch kurz in Stichpunkten zusammen: Worum ging es, wer hat was gesagt oder entschieden, "
              "was ist offen. Keine Reaktion setzen.\n\n" + "\n".join(picked))
    header = f"**Zusammenfassung der letzten {len(picked)} Nachrichten:**\n\n"
    live = Live(lambda p: interaction.followup.send(p, allowed_mentions=NO_PINGS, wait=True),
                lambda p: interaction.followup.send(p, allowed_mentions=NO_PINGS, wait=True), header)
    await answer_live(live, [], prompt, None)


@bot.tree.command(name="ai-persona", description="Legt fest, wie der Bot auf diesem Server redet (leer = zurücksetzen)")
@app_commands.describe(persoenlichkeit="z. B. 'frecher Pirat', 'Minecraft-Profi der viel Slang benutzt'")
@app_commands.default_permissions(manage_guild=True)
@app_commands.guild_only()
async def ai_persona(interaction: discord.Interaction, persoenlichkeit: str = None):
    if not interaction.user.guild_permissions.manage_guild:
        await interaction.response.send_message("Nur Admins (Server verwalten) dürfen das.", ephemeral=True)
        return
    key = str(interaction.guild_id)
    if persoenlichkeit and persoenlichkeit.strip():
        DATA["personas"][key] = persoenlichkeit.strip()[:500]
        msg = f"Neue Persönlichkeit: {DATA['personas'][key]}"
    else:
        DATA["personas"].pop(key, None)
        msg = "Persönlichkeit zurückgesetzt."
    save_soon()
    await interaction.response.send_message(msg, ephemeral=True)


async def image_agent(force_new=False):
    if DATA["image_agent"] and not force_new:
        return DATA["image_agent"]
    body = {"model": CFG["image_agent_model"], "name": "Discord Bild-Bot",
            "instructions": "Use the image generation tool when you have to create images.",
            "tools": [{"type": "image_generation"}]}
    await pace()
    async with bot.http_session.post(API + "/agents", json=body, headers=headers()) as r:
        text = await r.text()
        if r.status >= 300:
            raise RuntimeError("Agent anlegen fehlgeschlagen: " + mistral_error(r.status, text))
        DATA["image_agent"] = json.loads(text)["id"]
    save_soon()
    return DATA["image_agent"]


def file_chunks(outputs):
    found = []
    for out in outputs or []:
        content = out.get("content") if isinstance(out, dict) else None
        if isinstance(content, list):
            for c in content:
                if isinstance(c, dict) and c.get("type") == "tool_file" and c.get("file_id"):
                    found.append(c)
    return found


async def generate_image(prompt):
    async with api_lock:
        for attempt in range(2):
            agent = await image_agent(force_new=attempt > 0)
            await pace()
            async with bot.http_session.post(API + "/conversations", json={"agent_id": agent, "inputs": prompt, "store": False}, headers=headers()) as r:
                text = await r.text()
                if r.status == 404 and attempt == 0:
                    continue
                if r.status >= 300:
                    err = mistral_error(r.status, text)
                    print(f"[Mistral] Bild -> {err}")
                    raise RuntimeError(err)
                files = file_chunks(json.loads(text).get("outputs"))
                break
        if not files:
            raise RuntimeError("Mistral hat kein Bild zurückgegeben.")
        images = []
        for f in files[:4]:
            async with bot.http_session.get(f"{API}/files/{f['file_id']}/content", headers=headers()) as r:
                if r.status >= 300:
                    raise RuntimeError("Bild herunterladen fehlgeschlagen: " + mistral_error(r.status, await r.text()))
                images.append((await r.read(), f.get("file_type") or "png"))
        return images


@bot.tree.command(name="bild", description="Die KI malt ein Bild (Mistral, braucht Guthaben/Limit)")
@app_commands.describe(beschreibung="Was soll auf dem Bild sein?")
async def bild_cmd(interaction: discord.Interaction, beschreibung: str):
    left = cooldown_left(("img", interaction.user.id), float(CFG["image_cooldown_seconds"]))
    if left > 0:
        await interaction.response.send_message(f"Warte noch {left:.0f} Sekunden.", ephemeral=True)
        return
    await interaction.response.defer(thinking=True)
    try:
        images = await generate_image(beschreibung[:1000])
    except Exception as e:
        await interaction.followup.send(f"Fehler beim Bild: {e}", allowed_mentions=NO_PINGS)
        return
    files = [discord.File(io.BytesIO(data), filename=f"bild{i + 1}.{ext}") for i, (data, ext) in enumerate(images)]
    await interaction.followup.send(f"**{interaction.user.display_name}:** {beschreibung[:300]}", files=files, allowed_mentions=NO_PINGS)


@bot.event
async def on_message(message: discord.Message):
    if message.author.bot or bot.user is None:
        return
    is_dm = message.guild is None
    mentioned = bot.user in message.mentions and not message.mention_everyone
    replied = False
    if message.reference and message.reference.message_id:
        ref = message.reference.resolved
        if not isinstance(ref, discord.Message):
            try:
                ref = await message.channel.fetch_message(message.reference.message_id)
            except discord.HTTPException:
                ref = None
        replied = ref is not None and ref.author == bot.user
    if not ((is_dm and CFG["answer_dms"]) or (mentioned and CFG["answer_mentions"]) or (replied and CFG["answer_replies"])):
        return
    question = clean_question(message)[:4000]
    images = await image_inputs(message.attachments)
    if not question and not images:
        await add_reaction(message, "👋")
        return
    if cooldown_left(message.author.id, float(CFG["cooldown_seconds"])) > 0:
        await add_reaction(message, "⏳")
        return
    history = await reply_chain(message)
    gid = message.guild.id if message.guild else None
    live = Live(lambda p: message.reply(p, mention_author=False, allowed_mentions=NO_PINGS),
                lambda p: message.channel.send(p, allowed_mentions=NO_PINGS))
    answer, emoji = await answer_live(live, history, question, gid, images, thinking_on=message)
    if answer is None:
        return
    remember_answer([m.id for m in live.msgs], message.author.id, gid, history, with_image_note(question, images), answer)
    await add_reaction(message, emoji)
    await add_buttons(live.msgs)


@bot.event
async def on_raw_reaction_add(payload: discord.RawReactionActionEvent):
    if not CFG["reaction_buttons"] or bot.user is None or payload.user_id == bot.user.id:
        return
    emoji = str(payload.emoji)
    if emoji not in (REGENERATE, MORE, DELETE):
        return
    entry = answers.get(payload.message_id)
    if entry is None:
        return
    channel = bot.get_channel(payload.channel_id) or await bot.fetch_channel(payload.channel_id)
    try:
        msg = await channel.fetch_message(payload.message_id)
    except discord.HTTPException:
        return
    user = payload.member or await bot.fetch_user(payload.user_id)
    if user.bot:
        return
    try:
        await msg.remove_reaction(payload.emoji, user)
    except discord.HTTPException:
        pass
    if emoji == DELETE:
        if payload.user_id != entry["asker"]:
            return
        for mid in entry["ids"]:
            try:
                old = await channel.fetch_message(mid)
                await old.delete()
            except discord.HTTPException:
                pass
            answers.pop(mid, None)
        save_soon()
        return
    if cooldown_left(payload.user_id, float(CFG["cooldown_seconds"])) > 0:
        return
    gid = entry.get("guild")
    if emoji == REGENERATE and len(entry["ids"]) == 1:
        header = HEADER_RE.match(msg.content)
        live = Live(None, lambda p: channel.send(p, allowed_mentions=NO_PINGS), header.group(0) if header else "")
        live.adopt(msg)
        answer, react = await answer_live(live, entry["history"], entry["question"], gid, thinking_on=msg)
        if answer is None:
            return
        entry["answer"] = answer
        for extra in live.msgs[1:]:
            answers[extra.id] = entry
            entry["ids"].append(extra.id)
        save_soon()
        await add_reaction(msg, react)
        return
    if emoji == REGENERATE:
        history, question = entry["history"], entry["question"]
    else:
        history = entry["history"] + [{"role": "user", "content": entry["question"]}, {"role": "assistant", "content": entry["answer"]}]
        question = "Erklär das genauer und ausführlicher."
    live = Live(lambda p: msg.reply(p, mention_author=False, allowed_mentions=NO_PINGS),
                lambda p: channel.send(p, allowed_mentions=NO_PINGS))
    answer, react = await answer_live(live, history, question, gid, thinking_on=msg)
    if answer is None:
        return
    remember_answer([m.id for m in live.msgs], entry["asker"], gid, history, question, answer)
    await add_reaction(live.msgs[0], react)
    await add_buttons(live.msgs)


@bot.event
async def on_ready():
    print(f"Online als {bot.user} ({len(bot.guilds)} Server). Modell: {CFG['model']}")


load_data()
try:
    bot.run(CFG["discord_token"])
except discord.errors.PrivilegedIntentsRequired:
    raise SystemExit("Discord blockiert den Bot: Auf discord.com/developers -> deine App -> Bot -> "
                     "\"Message Content Intent\" einschalten und speichern.")
