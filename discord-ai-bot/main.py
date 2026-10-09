import asyncio
import json
import os
import re
import time
from collections import OrderedDict, defaultdict, deque

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
    cfg.setdefault("system_prompt", "Du bist ein hilfreicher Assistent in einem Discord-Server. Antworte kurz und in der Sprache der Frage.")
    cfg.setdefault("memory_messages", 6)
    cfg.setdefault("cooldown_seconds", 5)
    cfg.setdefault("max_tokens", 800)
    cfg.setdefault("answer_mentions", True)
    cfg.setdefault("answer_replies", True)
    cfg.setdefault("answer_dms", True)
    cfg.setdefault("bot_reactions", True)
    cfg.setdefault("reaction_buttons", True)
    return cfg


CFG = load_config()
if not CFG["discord_token"] or not CFG["mistral_api_key"]:
    raise SystemExit("Bitte discord_token und mistral_api_key in config.json eintragen.")

MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions"
NO_PINGS = discord.AllowedMentions.none()
REACT_RE = re.compile(r"^\s*\[react:\s*([^\]\s]{1,16})\s*\]\s*")
HEADER_RE = re.compile(r"^\*\*[^*\n]{1,100}:\*\* [^\n]*\n\n")
REGENERATE, MORE, DELETE = "🔄", "➕", "❌"

memory = defaultdict(lambda: deque(maxlen=max(0, int(CFG["memory_messages"]))))
last_use = {}
api_lock = asyncio.Lock()
last_call = [0.0]
answers = OrderedDict()


def remember_answer(message_ids, asker_id, history, question, answer):
    entry = {"asker": asker_id, "history": history, "question": question, "answer": answer, "ids": list(message_ids)}
    for mid in message_ids:
        answers[mid] = entry
        answers.move_to_end(mid)
    while len(answers) > 1000:
        answers.popitem(last=False)


class Bot(discord.Client):
    def __init__(self):
        intents = discord.Intents.default()
        intents.message_content = bool(CFG["answer_mentions"] or CFG["answer_replies"] or CFG["answer_dms"])
        super().__init__(intents=intents)
        self.tree = app_commands.CommandTree(self)
        self.http_session = None

    async def setup_hook(self):
        self.http_session = aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=60))
        await self.tree.sync()

    async def close(self):
        if self.http_session:
            await self.http_session.close()
        await super().close()


bot = Bot()


def mistral_error(status, text):
    try:
        data = json.loads(text)
        msg = data.get("message") or data.get("detail") or data.get("error") or text
        if isinstance(msg, (dict, list)):
            msg = json.dumps(msg)
    except Exception:
        msg = text
    return f"{status}: {str(msg)[:200]}"


async def call_model(model, messages):
    payload = {"model": model, "messages": messages, "max_tokens": int(CFG["max_tokens"])}
    headers = {"Authorization": "Bearer " + CFG["mistral_api_key"], "Content-Type": "application/json"}
    waits = [2, 5, 10]
    for attempt in range(len(waits) + 1):
        wait = 1.1 - (time.monotonic() - last_call[0])
        if wait > 0:
            await asyncio.sleep(wait)
        last_call[0] = time.monotonic()
        async with bot.http_session.post(MISTRAL_URL, json=payload, headers=headers) as r:
            text = await r.text()
            if r.status == 200:
                return json.loads(text)["choices"][0]["message"]["content"].strip()
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


async def generate(history, question):
    system = CFG["system_prompt"] + (REACT_HINT if CFG["bot_reactions"] else "")
    messages = [{"role": "system", "content": system}] + list(history) + [{"role": "user", "content": question}]
    models = [CFG["model"]] + [m for m in CFG["fallback_models"] if m != CFG["model"]]
    errors = []
    async with api_lock:
        for model in models:
            try:
                raw = await call_model(model, messages)
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


async def ask_mistral(user_id, question):
    history = list(memory[user_id])
    answer, emoji = await generate(history, question)
    memory[user_id].append({"role": "user", "content": question})
    memory[user_id].append({"role": "assistant", "content": answer})
    return answer, emoji, history


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


def cooldown_left(user_id):
    now = time.monotonic()
    cd = float(CFG["cooldown_seconds"])
    left = cd - (now - last_use.get(user_id, -cd))
    if left <= 0:
        last_use[user_id] = now
    return left


async def add_reaction(message, emoji):
    if not emoji or not CFG["bot_reactions"]:
        return
    try:
        await message.add_reaction(emoji)
    except discord.HTTPException:
        pass


def clean_question(message):
    text = message.content
    if bot.user:
        text = re.sub(rf"<@!?{bot.user.id}>", "", text)
    return text.strip()


def strip_header(text):
    return HEADER_RE.sub("", text, count=1)


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
            history.insert(0, {"role": "assistant", "content": stored["answer"]})
            history[0:0] = stored["history"] + [{"role": "user", "content": stored["question"]}]
            break
        role = "assistant" if prev.author == bot.user else "user"
        content = strip_header(prev.content) if role == "assistant" else clean_question(prev)
        if content:
            history.insert(0, {"role": role, "content": content})
        ref = prev.reference
    return history[-limit * 2:] if limit else []


async def send_answer(channel_send, answer):
    sent = []
    for part in chunks(answer):
        sent.append(await channel_send(part))
    return sent


async def add_buttons(messages):
    if not CFG["reaction_buttons"] or not messages:
        return
    last = messages[-1]
    for e in (REGENERATE, MORE, DELETE):
        try:
            await last.add_reaction(e)
        except discord.HTTPException:
            return


@bot.tree.command(name="ai", description="Frag die KI (Mistral)")
@app_commands.describe(nachricht="Deine Frage oder Nachricht an die KI")
async def ai(interaction: discord.Interaction, nachricht: str):
    left = cooldown_left(interaction.user.id)
    if left > 0:
        await interaction.response.send_message(f"Warte noch {left:.0f} Sekunden.", ephemeral=True)
        return
    await interaction.response.defer(thinking=True)
    question = nachricht[:4000]
    try:
        answer, emoji, history = await ask_mistral(interaction.user.id, question)
    except Exception as e:
        await interaction.followup.send(f"Fehler: {e}", allowed_mentions=NO_PINGS)
        return
    header = f"**{interaction.user.display_name}:** {nachricht[:300]}\n\n"
    sent = await send_answer(lambda p: interaction.followup.send(p, allowed_mentions=NO_PINGS, wait=True), header + answer)
    remember_answer([m.id for m in sent], interaction.user.id, history, question, answer)
    await add_reaction(sent[0], emoji)
    await add_buttons(sent)


@bot.tree.command(name="ai-reset", description="Die KI vergisst euer bisheriges Gespräch")
async def ai_reset(interaction: discord.Interaction):
    memory.pop(interaction.user.id, None)
    await interaction.response.send_message("Gespräch vergessen.", ephemeral=True)


@bot.event
async def on_message(message: discord.Message):
    if message.author.bot or bot.user is None:
        return
    is_dm = message.guild is None
    mentioned = bot.user in message.mentions and not message.mention_everyone
    replied = False
    if message.reference and message.reference.message_id:
        ref = message.reference.resolved
        if isinstance(ref, discord.Message):
            replied = ref.author == bot.user
        else:
            try:
                ref = await message.channel.fetch_message(message.reference.message_id)
                replied = ref.author == bot.user
            except discord.HTTPException:
                replied = False
    if not ((is_dm and CFG["answer_dms"]) or (mentioned and CFG["answer_mentions"]) or (replied and CFG["answer_replies"])):
        return
    question = clean_question(message)[:4000]
    if not question:
        await add_reaction(message, "👋")
        return
    if cooldown_left(message.author.id) > 0:
        await add_reaction(message, "⏳")
        return
    history = await reply_chain(message)
    try:
        async with message.channel.typing():
            answer, emoji = await generate(history, question)
    except Exception as e:
        await message.reply(f"Fehler: {e}", mention_author=False, allowed_mentions=NO_PINGS)
        return
    sent = []
    for i, part in enumerate(chunks(answer)):
        if i == 0:
            sent.append(await message.reply(part, mention_author=False, allowed_mentions=NO_PINGS))
        else:
            sent.append(await message.channel.send(part, allowed_mentions=NO_PINGS))
    remember_answer([m.id for m in sent], message.author.id, history, question, answer)
    await add_reaction(message, emoji)
    await add_buttons(sent)


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
    is_asker = payload.user_id == entry["asker"]
    if emoji == DELETE:
        if not is_asker:
            return
        for mid in entry["ids"]:
            try:
                old = await channel.fetch_message(mid)
                await old.delete()
            except discord.HTTPException:
                pass
            answers.pop(mid, None)
        return
    if cooldown_left(payload.user_id) > 0:
        return
    try:
        async with channel.typing():
            if emoji == REGENERATE:
                answer, react = await generate(entry["history"], entry["question"])
                history, question = entry["history"], entry["question"]
            else:
                history = entry["history"] + [{"role": "user", "content": entry["question"]}, {"role": "assistant", "content": entry["answer"]}]
                question = "Erklär das genauer und ausführlicher."
                answer, react = await generate(history, question)
    except Exception as e:
        await msg.reply(f"Fehler: {e}", mention_author=False, allowed_mentions=NO_PINGS)
        return
    if emoji == REGENERATE and len(entry["ids"]) == 1 and len(answer) <= 1900:
        header = HEADER_RE.match(msg.content)
        await msg.edit(content=(header.group(0) if header else "") + answer, allowed_mentions=NO_PINGS)
        entry["answer"] = answer
        await add_reaction(msg, react)
        return
    sent = []
    for i, part in enumerate(chunks(answer)):
        if i == 0:
            sent.append(await msg.reply(part, mention_author=False, allowed_mentions=NO_PINGS))
        else:
            sent.append(await channel.send(part, allowed_mentions=NO_PINGS))
    remember_answer([m.id for m in sent], entry["asker"], history, question, answer)
    await add_reaction(sent[0], react)
    await add_buttons(sent)


@bot.event
async def on_ready():
    print(f"Online als {bot.user} ({len(bot.guilds)} Server). Modell: {CFG['model']}")


try:
    bot.run(CFG["discord_token"])
except discord.errors.PrivilegedIntentsRequired:
    raise SystemExit("Discord blockiert den Bot: Auf discord.com/developers -> deine App -> Bot -> "
                     "\"Message Content Intent\" einschalten und speichern. Oder in config.json "
                     "answer_mentions, answer_replies und answer_dms auf false setzen (dann geht nur /ai).")
