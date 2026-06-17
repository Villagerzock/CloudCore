import mineflayer from "mineflayer";
import mc from "minecraft-protocol";

const BOT_COUNT = 30;
const HOST = "127.0.0.1";
const PORT = 25565;

const args = process.argv.slice(2);

if (args[0] === "ping") {
    await pingServer();
} else {
    await spawnBots();
}

async function pingServer(): Promise<void> {
    const response = await mc.ping({
        host: HOST,
        port: PORT
    });

    console.log(`Version: ${response.version.name} (${response.version.protocol})`);
    console.log(`Players: ${response.players.online}/${response.players.max}`);
    console.log("MOTD:");

    console.log(formatMotd(response.description));
}

function formatMotd(description: unknown): string {
    if (typeof description === "string") {
        return legacyToAnsi(description);
    }

    if (typeof description === "object" && description !== null) {
        return componentToAnsi(description as MinecraftTextComponent);
    }

    return String(description);
}

type MinecraftTextComponent = {
    text?: string;
    color?: string;
    bold?: boolean;
    italic?: boolean;
    underlined?: boolean;
    strikethrough?: boolean;
    obfuscated?: boolean;
    extra?: MinecraftTextComponent[];
};

function componentToAnsi(component: MinecraftTextComponent): string {
    let out = "";

    out += styleToAnsi(component);
    out += legacyToAnsi(component.text ?? "");

    if (component.extra) {
        for (const child of component.extra) {
            out += componentToAnsi(child);
        }
    }

    out += "\x1b[0m";
    return out;
}

function styleToAnsi(component: MinecraftTextComponent): string {
    let out = "";

    if (component.color) {
        out += colorToAnsi(component.color);
    }

    if (component.bold) out += "\x1b[1m";
    if (component.italic) out += "\x1b[3m";
    if (component.underlined) out += "\x1b[4m";
    if (component.strikethrough) out += "\x1b[9m";

    return out;
}

function legacyToAnsi(text: string): string {
    const colors: Record<string, string> = {
        "0": "\x1b[30m",
        "1": "\x1b[34m",
        "2": "\x1b[32m",
        "3": "\x1b[36m",
        "4": "\x1b[31m",
        "5": "\x1b[35m",
        "6": "\x1b[33m",
        "7": "\x1b[37m",
        "8": "\x1b[90m",
        "9": "\x1b[94m",
        a: "\x1b[92m",
        b: "\x1b[96m",
        c: "\x1b[91m",
        d: "\x1b[95m",
        e: "\x1b[93m",
        f: "\x1b[97m"
    };

    let out = "";

    for (let i = 0; i < text.length; i++) {
        if (text[i] === "§" && i + 1 < text.length) {
            const code = text[++i].toLowerCase();

            if (colors[code]) out += colors[code];
            else if (code === "l") out += "\x1b[1m";
            else if (code === "o") out += "\x1b[3m";
            else if (code === "n") out += "\x1b[4m";
            else if (code === "m") out += "\x1b[9m";
            else if (code === "r") out += "\x1b[0m";

            continue;
        }

        out += text[i];
    }

    return out + "\x1b[0m";
}

function colorToAnsi(color: string): string {
    const colors: Record<string, string> = {
        black: "\x1b[30m",
        dark_blue: "\x1b[34m",
        dark_green: "\x1b[32m",
        dark_aqua: "\x1b[36m",
        dark_red: "\x1b[31m",
        dark_purple: "\x1b[35m",
        gold: "\x1b[33m",
        gray: "\x1b[37m",
        dark_gray: "\x1b[90m",
        blue: "\x1b[94m",
        green: "\x1b[92m",
        aqua: "\x1b[96m",
        red: "\x1b[91m",
        light_purple: "\x1b[95m",
        yellow: "\x1b[93m",
        white: "\x1b[97m"
    };

    return colors[color] ?? "";
}

async function spawnBots() {
    for (let i = 0; i < BOT_COUNT; i++) {
        try {
            await spawnBot(i);
        } catch (error) {
            console.error(`Failed to spawn Bot_${i}:`, error);
        }

        await delay(3000);
    }
}

async function spawnBot(i: number): Promise<void> {
    const username = `Bot_${i}`;

    const bot = mineflayer.createBot({
        host: HOST,
        port: PORT,
        username,
        auth: "offline"
    });

    return new Promise<void>((resolve, reject) => {
        const timeout = setTimeout(() => {
            bot.end();
            reject(new Error(`${username} login timeout`));
        }, 15000);

        let done = false;

        function finish(error?: Error) {
            if (done) return;
            done = true;

            clearTimeout(timeout);

            if (error) reject(error);
            else resolve();
        }

        bot.once("spawn", () => {
            console.log(`${username} joined`);
            finish();
        });

        bot.once("kicked", reason => {
            console.log(`${username} kicked:`, reason);
            finish(new Error(`Kicked: ${reason}`));
        });

        bot.once("error", error => {
            console.log(`${username} error:`, error.message);
            finish(error);
        });

        bot.once("end", () => {
            finish(new Error(`${username} disconnected`));
        });
    });
}

function delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}