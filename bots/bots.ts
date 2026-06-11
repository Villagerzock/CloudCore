import mineflayer from "mineflayer";

const BOT_COUNT = 30;
const HOST = "127.0.0.1";
const PORT = 25565;

await spawnBots();

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

        bot.once("spawn", () => {
            clearTimeout(timeout);
            console.log(`${username} joined`);
            resolve();
        });

        bot.once("kicked", (reason) => {
            clearTimeout(timeout);
            console.log(`${username} kicked:`, reason);
            reject(new Error(`Kicked: ${reason}`));
        });

        bot.once("error", (error) => {
            clearTimeout(timeout);
            console.log(`${username} error:`, error.message);
            reject(error);
        });

        bot.once("end", () => {
            clearTimeout(timeout);
            reject(new Error(`${username} disconnected`));
        });
    });
}

function delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}