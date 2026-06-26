import styles from "./LiveConsole.module.css"
import TextInput from "./TextInput.tsx";
import {connectLiveConsole, type LiveConsoleConnection} from "../lib/LiveConsoleConnection.ts";
import {type FormEvent, useEffect, useMemo, useRef, useState} from "react";
import AnsiImport from "ansi-to-react";
import {useI18n} from "../lib/i18n.ts";

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
    const {t} = useI18n();
    const MAX_LINES = 1000;

    const outputRef = useRef<HTMLPreElement>(null);
    const connectionRef = useRef<LiveConsoleConnection | null>(null);

    const [consoleOutput, setConsoleOutput] = useState<{
        consoleId: string;
        lines: string[];
    } | null>(null);
    const [command, setCommand] = useState("");
    const lines = useMemo(
        () => consoleOutput?.consoleId === consoleId ? consoleOutput.lines : [],
        [consoleId, consoleOutput]
    );

    function send(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        const value = command.trim();
        if (!value || consoleId === null) return;

        connectionRef.current?.sendCommand(consoleId, value);
        setCommand("");
    }

    useEffect(() => {
        if (consoleId === null) return;

        const connection = connectLiveConsole(
            import.meta.env.VITE_CONSOLE_WS_URL ?? "/ws/console",
            consoleId
        );
        connectionRef.current = connection;

        const unsubscribe = connection.addListener(consoleId, (newLines) => {
            setConsoleOutput((currentOutput) => ({
                consoleId,
                lines: [
                    ...(currentOutput?.consoleId === consoleId ? currentOutput.lines : []),
                    ...newLines
                ].slice(-MAX_LINES)
            }));
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
                {consoleId === null ? t("state.connecting") : lines.length > 0 ? `${lines.join("\n")}\n` : ""}
            </Ansi>
		</pre>

            {consoleId !== null && (
                <form className={styles.commandForm} onSubmit={send}>
                    <TextInput
                        value={command}
                        onChange={(event) => setCommand(event.target.value)}
                        placeholder={t("console.command_placeholder")}
                        aria-label={t("console.command_label")}
                    />
                </form>
            )}
        </div>
    );
}

export default LiveConsole;
