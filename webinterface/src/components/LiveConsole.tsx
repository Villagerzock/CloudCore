import styles from "./LiveConsole.module.css"
import TextInput from "./TextInput.tsx";
import {connectLiveConsole, type LiveConsoleConnection} from "../lib/LiveConsoleConnection.ts";
import {type FormEvent, useEffect, useRef, useState} from "react";
import AnsiImport from "ansi-to-react";

type AnsiComponent = typeof AnsiImport;
const Ansi = typeof AnsiImport === "function"
    ? AnsiImport
    : (AnsiImport as unknown as { default: AnsiComponent }).default;

type LiveConsoleProps = {
    width?: number | string | undefined;
    height?: number | string | undefined;
    consoleId: string | null;
}

function LiveConsole({ width = "100%", height = "100%", consoleId }: LiveConsoleProps){
    const MAX_LINES = 1000;

    const outputRef = useRef<HTMLPreElement>(null);
    const connectionRef = useRef<LiveConsoleConnection | null>(null);

    const [lines, setLines] = useState<string[]>([]);
    const [command, setCommand] = useState("");

    function send(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        const value = command.trim();
        if (!value || consoleId === null) return;

        connectionRef.current?.sendCommand(consoleId, value);
        setCommand("");
    }

    useEffect(() => {
        setLines([]);
        if (consoleId === null) return;

        const connection = connectLiveConsole(import.meta.env.VITE_CONSOLE_WS_URL ?? "/ws/console");
        connectionRef.current = connection;

        const unsubscribe = connection.addListener(consoleId, (newLines) => {
            setLines((currentLines) => [
                ...currentLines,
                ...newLines
            ].slice(-MAX_LINES));
        });

        return () => {
            unsubscribe();
            connection.close();
            connectionRef.current = null;
        };
    }, [consoleId]);

    useEffect(() => {
        const output = outputRef.current;
        if (!output) return;

        output.scrollTop = output.scrollHeight;
    }, [lines]);

    return (
        <div className={styles.container} style={{ width, height }}>
		<pre ref={outputRef} className={styles.console}>
			<Ansi className={styles.ansi}>
                {consoleId === null ? "Connecting..." : lines.length > 0 ? `${lines.join("\n")}\n` : ""}
            </Ansi>
		</pre>

            {consoleId !== null && (
                <form className={styles.commandForm} onSubmit={send}>
                    <TextInput
                        value={command}
                        onChange={(event) => setCommand(event.target.value)}
                        placeholder="Enter command..."
                        aria-label="Console command"
                    />
                </form>
            )}
        </div>
    );
}

export default LiveConsole;
