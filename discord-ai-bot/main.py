import asyncio
import json
import os
import time
from collections import defaultdict, deque

import aiohttp
import discord
from discord import app_commands

HERE = os.path.dirname(os.path.abspath(__file__))


def load_config():
    cfg = {}
    path = os.path.join(HERE, "config.json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            cfg = json.load(f)
    cfg["discord_token"] = os.environ.get("DISCORD_TOKEN") or cfg.get("discord_token", "")
    cfg["mistral_api_key"] = os.environ.get("MISTRAL_API_KEY") or cfg.get("mistral_api_key", "")
    cfg.setdefault("model", "mistral-small-latest")
    cfg.setdefault("system_prompt", "Du bist ein hilfreicher Assistent in einem Discord-Server. Antworte kurz und in der Sprache der Frage.")
    cfg.setdefault("memory_messages", 6)
    cfg.setdefault("cooldown_seconds", 5)
    cfg.setdefault("max_tokens", 800)
    return cfg


CFG = load_config()
if not CFG["discord_token"] or not CFG["mistral_api_key"]:
    raise SystemExit("Bitte discord_token und mistral_api_key in config.json eintragen.")

MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions"
NO_PINGS = discord.AllowedMentions.none()

memory = defaultdict(lambda: deque(maxlen=max(0, int(CFG["memory_messages"]))))
last_use = {}
api_lock = asyncio.Lock()
last_call = [0.0]


class Bot(discord.Client):
    def __init__(self):
        super().__init__(intents=discord.Intents.default())
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


async def ask_mistral(user_id, question):
    messages = [{"role": "system", "content": CFG["system_prompt"]}]
    messages += list(memory[user_id])
    messages.append({"role": "user", "content": question})
    payload = {"model": CFG["model"], "messages": messages, "max_tokens": int(CFG["max_tokens"])}
    headers = {"Authorization": "Bearer " + CFG["mistral_api_key"], "Content-Type": "application/json"}
    async with api_lock:
        wait = 1.1 - (time.monotonic() - last_call[0])
        if wait > 0:
            await asyncio.sleep(wait)
        for attempt in range(3):
            last_call[0] = time.monotonic()
            async with bot.http_session.post(MISTRAL_URL, json=payload, headers=headers) as r:
                if r.status == 429 and attempt < 2:
                    await asyncio.sleep(float(r.headers.get("Retry-After", 3 * (attempt + 1))))
                    continue
                text = await r.text()
                if r.status == 401:
                    raise RuntimeError("Der Mistral API-Key ist falsch.")
                if r.status == 429:
                    raise RuntimeError("Mistral-Limit erreicht, versuch es gleich nochmal.")
                if r.status >= 300:
                    raise RuntimeError(f"Mistral-Fehler {r.status}: {text[:200]}")
                answer = json.loads(text)["choices"][0]["message"]["content"].strip()
                break
    memory[user_id].append({"role": "user", "content": question})
    memory[user_id].append({"role": "assistant", "content": answer})
    return answer or "(leere Antwort)"


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


@bot.tree.command(name="ai", description="Frag die KI (Mistral)")
@app_commands.describe(nachricht="Deine Frage oder Nachricht an die KI")
async def ai(interaction: discord.Interaction, nachricht: str):
    now = time.monotonic()
    cd = float(CFG["cooldown_seconds"])
    left = cd - (now - last_use.get(interaction.user.id, -cd))
    if left > 0:
        await interaction.response.send_message(f"Warte noch {left:.0f} Sekunden.", ephemeral=True)
        return
    last_use[interaction.user.id] = now
    await interaction.response.defer(thinking=True)
    try:
        answer = await ask_mistral(interaction.user.id, nachricht[:4000])
    except Exception as e:
        await interaction.followup.send(f"Fehler: {e}", allowed_mentions=NO_PINGS)
        return
    header = f"**{interaction.user.display_name}:** {nachricht[:300]}\n\n"
    parts = chunks(header + answer)
    for part in parts:
        await interaction.followup.send(part, allowed_mentions=NO_PINGS)


@bot.tree.command(name="ai-reset", description="Die KI vergisst euer bisheriges Gespräch")
async def ai_reset(interaction: discord.Interaction):
    memory.pop(interaction.user.id, None)
    await interaction.response.send_message("Gespräch vergessen.", ephemeral=True)


@bot.event
async def on_ready():
    print(f"Online als {bot.user} ({len(bot.guilds)} Server). Modell: {CFG['model']}")


bot.run(CFG["discord_token"])
