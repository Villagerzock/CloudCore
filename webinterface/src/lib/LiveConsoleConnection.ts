export type LiveConsoleListener = (lines: string[]) => void;

const CONNECTION_OPEN = 1;
const CONNECTION_CLOSED = 3;
const ANSI_RESET = "\u001b[0m";
const ANSI_BOLD_GREEN = "\u001b[1;32m";
const ANSI_GREEN = "\u001b[32m";
const ANSI_YELLOW = "\u001b[33m";
const ANSI_CYAN = "\u001b[36m";
const ANSI_DIM = "\u001b[2m";
const ANSI_BLINK = "\u001b[5m";

const DUMMY_STARTUP_OUTPUT = [
    {
        delay: 150,
        lines: [
            `${ANSI_BOLD_GREEN}  .   ____          _            __ _ _`,
            " /\\ / ___'_ __ _ _(_)_ __  __ _ \\ \\ \\",
            "( ( )\\___ | '_ | '_| | '_ \\/ _` | \\ \\ \\",
            " \\/  ___)| |_)| | | | | || (_| |  ) ) ) )",
            "  '  |____| .__|_| |_|_| |_\\__, | / / / /",
            " =========|_|==============|___/=/_/_/_/",
            ` :: Spring Boot ::                ${ANSI_CYAN}(v3.5.0)${ANSI_RESET}`
        ]
    },
    {
        delay: 600,
        lines: [
            `${ANSI_DIM}2026-06-18T12:00:00.000+02:00${ANSI_RESET}  ${ANSI_GREEN}INFO${ANSI_RESET} --- [main] ${ANSI_CYAN}CloudCoreApplication${ANSI_RESET} : Starting CloudCoreApplication`,
            `${ANSI_DIM}2026-06-18T12:00:00.080+02:00${ANSI_RESET}  ${ANSI_YELLOW}WARN${ANSI_RESET} --- [main] ${ANSI_CYAN}CloudCoreApplication${ANSI_RESET} : No active profile set, falling back to default profile`
        ]
    },
    {
        delay: 1100,
        lines: [`${ANSI_DIM}2026-06-18T12:00:00.500+02:00${ANSI_RESET}  ${ANSI_GREEN}INFO${ANSI_RESET} --- [main] ${ANSI_CYAN}TomcatWebServer${ANSI_RESET} : Tomcat initialized with port 8080 (http)`]
    },
    {
        delay: 1650,
        lines: [`${ANSI_DIM}2026-06-18T12:00:01.050+02:00${ANSI_RESET}  ${ANSI_GREEN}INFO${ANSI_RESET} --- [main] ${ANSI_CYAN}ServletWebServerApplicationContext${ANSI_RESET} : Root WebApplicationContext initialized`]
    },
    {
        delay: 2300,
        lines: [
            `${ANSI_DIM}2026-06-18T12:00:01.700+02:00${ANSI_RESET}  ${ANSI_GREEN}INFO${ANSI_RESET} --- [main] ${ANSI_CYAN}TomcatWebServer${ANSI_RESET} : Tomcat started on port 8080 (http)`,
            `${ANSI_DIM}2026-06-18T12:00:01.700+02:00${ANSI_RESET}  ${ANSI_BOLD_GREEN}INFO${ANSI_RESET} --- [main] ${ANSI_CYAN}CloudCoreApplication${ANSI_RESET} : Started CloudCoreApplication in 2.3 seconds`,
            `${ANSI_BLINK}_`
        ]
    }
] as const;

type ConsoleMessage = {
    console: string;
    lines: string[];
}

type ConsoleCommand = {
    console: string;
    command?: string;
    subscribe?: boolean;
}

function isConsoleMessage(value: unknown): value is ConsoleMessage {
    if (typeof value !== "object" || value === null) return false;

    const message = value as Record<string, unknown>;
    return typeof message.console === "string"
        && Array.isArray(message.lines)
        && message.lines.every((line) => typeof line === "string");
}

function toWebSocketUrl(url: string): string {
    const websocketUrl = new URL(url, window.location.href);

    if (websocketUrl.protocol === "http:") websocketUrl.protocol = "ws:";
    if (websocketUrl.protocol === "https:") websocketUrl.protocol = "wss:";

    if (websocketUrl.protocol !== "ws:" && websocketUrl.protocol !== "wss:") {
        throw new TypeError(`Unsupported WebSocket protocol: ${websocketUrl.protocol}`);
    }

    return websocketUrl.toString();
}

export abstract class LiveConsoleConnection {
    private readonly listeners = new Map<string, Set<LiveConsoleListener>>();
    private readonly lineHistory = new Map<string, string[]>();
    private connectionClosed = false;

    abstract get readyState(): number;

    addListener(consoleName: string, listener: LiveConsoleListener): () => void {
        this.assertOpen();

        const consoleListeners = this.listeners.get(consoleName) ?? new Set<LiveConsoleListener>();
        consoleListeners.add(listener);
        this.listeners.set(consoleName, consoleListeners);
        this.callListener(listener, this.lineHistory.get(consoleName) ?? []);
        this.onListenerAdded(consoleName);

        return () => this.removeListener(consoleName, listener);
    }

    removeListener(consoleName: string, listener: LiveConsoleListener): void {
        const consoleListeners = this.listeners.get(consoleName);
        if (!consoleListeners) return;

        consoleListeners.delete(listener);
        if (consoleListeners.size === 0) this.listeners.delete(consoleName);
    }

    abstract sendCommand(consoleName: string, command: string): void;
    abstract close(code?: number, reason?: string): void;

    protected assertOpen(): void {
        if (this.connectionClosed) {
            throw new Error("LiveConsoleConnection is closed");
        }
    }

    protected onListenerAdded(consoleName: string): void {
        void consoleName;
    }

    protected markClosed(): boolean {
        if (this.connectionClosed) return false;

        this.connectionClosed = true;
        this.listeners.clear();
        this.lineHistory.clear();
        return true;
    }

    protected emitLines(consoleName: string, lines: string[]): void {
        if (this.connectionClosed) return;

        const history = this.lineHistory.get(consoleName) ?? [];
        history.push(...lines);
        this.lineHistory.set(consoleName, history);

        const consoleListeners = this.listeners.get(consoleName);
        if (!consoleListeners) return;

        for (const listener of [...consoleListeners]) {
            this.callListener(listener, lines);
        }
    }

    private callListener(listener: LiveConsoleListener, lines: string[]): void {
        try {
            listener([...lines]);
        } catch (error) {
            console.error("LiveConsole listener failed", error);
        }
    }
}

class WebSocketLiveConsoleConnection extends LiveConsoleConnection {
    private readonly socket: WebSocket;
    private readonly pendingCommands: ConsoleCommand[] = [];

    constructor(url: string) {
        super();
        this.socket = new WebSocket(toWebSocketUrl(url));
        this.socket.addEventListener("open", this.handleOpen);
        this.socket.addEventListener("message", this.handleMessage);
        this.socket.addEventListener("close", this.handleClose);
    }

    get readyState(): number {
        return this.socket.readyState;
    }

    sendCommand(consoleName: string, command: string): void {
        this.assertOpen();

        const payload = { console: consoleName, command };
        this.sendOrQueue(payload);
    }

    protected override onListenerAdded(consoleName: string): void {
        this.sendOrQueue({ console: consoleName, subscribe: true });
    }

    private sendOrQueue(payload: ConsoleCommand): void {
        if (this.socket.readyState === WebSocket.CONNECTING) {
            this.pendingCommands.push(payload);
            return;
        }

        if (this.socket.readyState !== WebSocket.OPEN) {
            throw new Error("WebSocket is not open");
        }

        this.socket.send(JSON.stringify(payload));
    }

    close(code?: number, reason?: string): void {
        if (!this.markClosed()) return;

        this.pendingCommands.length = 0;
        this.removeSocketListeners();
        this.socket.close(code, reason);
    }

    private readonly handleOpen = (): void => {
        for (const command of this.pendingCommands.splice(0)) {
            this.socket.send(JSON.stringify(command));
        }
    }

    private readonly handleMessage = (event: MessageEvent<unknown>): void => {
        if (typeof event.data !== "string") return;

        let message: unknown;
        try {
            message = JSON.parse(event.data);
        } catch {
            return;
        }

        if (isConsoleMessage(message)) {
            this.emitLines(message.console, message.lines);
        }
    }

    private readonly handleClose = (): void => {
        this.markClosed();
        this.pendingCommands.length = 0;
        this.removeSocketListeners();
    }

    private removeSocketListeners(): void {
        this.socket.removeEventListener("open", this.handleOpen);
        this.socket.removeEventListener("message", this.handleMessage);
        this.socket.removeEventListener("close", this.handleClose);
    }
}

class DummyLiveConsoleConnection extends LiveConsoleConnection {
    private open = true;
    private readonly startedConsoles = new Set<string>();
    private readonly startupTimers = new Set<ReturnType<typeof setTimeout>>();

    get readyState(): number {
        return this.open ? CONNECTION_OPEN : CONNECTION_CLOSED;
    }

    sendCommand(consoleName: string, command: string): void {
        this.assertOpen();
        this.emitLines(consoleName, [command]);
    }

    protected override onListenerAdded(consoleName: string): void {
        if (this.startedConsoles.has(consoleName)) return;
        this.startedConsoles.add(consoleName);

        for (const output of DUMMY_STARTUP_OUTPUT) {
            const timer = setTimeout(() => {
                this.startupTimers.delete(timer);
                this.emitLines(consoleName, [...output.lines]);
            }, output.delay);

            this.startupTimers.add(timer);
        }
    }

    close(code?: number, reason?: string): void {
        void code;
        void reason;

        if (!this.markClosed()) return;
        for (const timer of this.startupTimers) clearTimeout(timer);
        this.startupTimers.clear();
        this.open = false;
    }
}

export function connectLiveConsole(url: string, consoleName: string): LiveConsoleConnection {
    const websocketUrl = new URL(url, window.location.href);
    const node = new URLSearchParams(window.location.search).get("node");
    if (node !== null) websocketUrl.searchParams.set("node", node);
    websocketUrl.searchParams.set("console", consoleName);
    return new WebSocketLiveConsoleConnection(websocketUrl.toString());
}

export function connectDummyConsole(): LiveConsoleConnection {
    return new DummyLiveConsoleConnection();
}
