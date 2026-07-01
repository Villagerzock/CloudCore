import {useEffect, useState} from "react";
import {useParams} from "react-router";
import LineChart from "../components/LineChart.tsx";
import LiveConsole from "../components/LiveConsole.tsx";
import {useServerMetrics} from "../hooks/useServerMetrics.ts";
import {
    getServerByName,
    getServerTemplates,
    launchServer,
    restartServer,
    stopServer,
    type Server
} from "../lib/api.ts";
import styles from "./ServerRoute.module.css";
import {useI18n} from "../lib/i18n.ts";
import Button from "../components/Button.tsx";

function ServerRoute(){
    const { name } = useParams<{ name: string }>();
    const {t} = useI18n();
    const serverName = name?.trim() || null;
    const { playerCountData, error: metricsError } = useServerMetrics(serverName);
    const [result, setResult] = useState<{
        name: string;
        server: Server | null;
        error: string | null;
        canStartSingleton: boolean;
    } | null>(null);
    const [action, setAction] = useState<string | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);

    async function loadServer(cancelled: () => boolean) {
        if (serverName === null) return;

        try {
            const server = await getServerByName(serverName);
            if (!cancelled()) setResult({name: serverName, server, error: null, canStartSingleton: server.singleton});
        } catch (reason: unknown) {
            if (cancelled()) return;
            const templates = await getServerTemplates().catch(() => []);
            setResult({
                name: serverName,
                server: null,
                error: reason instanceof Error ? reason.message : "Failed to load server",
                canStartSingleton: templates.some(template => template.name === serverName)
            });
        }
    }

    useEffect(() => {
        let cancelled = false;

        loadServer(() => cancelled);

        return () => {
            cancelled = true;
        };
    }, [serverName]);

    async function runAction(name: string, operation: () => Promise<void>) {
        try {
            setAction(name);
            setActionError(null);
            await operation();
            await loadServer(() => false);
        } catch (reason) {
            setActionError(reason instanceof Error ? reason.message : "Aktion fehlgeschlagen");
        } finally {
            setAction(null);
        }
    }

    if (serverName === null) {
        return <p role="alert">{t("server.invalid_name")}</p>;
    }

    const currentResult = result?.name === serverName ? result : null;

    if (currentResult?.error) {
        return (
            <div className={styles.fallback}>
                <p role="alert">{currentResult.error}</p>
                {currentResult.canStartSingleton && (
                    <Button
                        type="primary"
                        onClick={() => runAction("start", async () => {
                            await launchServer(serverName, true);
                        })}
                        disabled={action !== null}
                    >
                        Start
                    </Button>
                )}
                {actionError && <p role="alert">{actionError}</p>}
            </div>
        );
    }

    if (!currentResult?.server) {
        return <p>{t("state.loading_server")}</p>;
    }

    const server = currentResult.server;

    return (
        <div className={styles.server}>
            <div className={styles.console}>
                <LiveConsole consoleId={`server-${server.name}`}/>
            </div>
            <div className={styles.stats}>
                <h2>{server.name}</h2>
                <span>{t("field.template")}: {server.template}</span>
                <span>{t("metrics.online")}: {server.online}/{server.max}</span>
                <div className={styles.actions}>
                    {server.singleton && (
                        <Button type="primary" onClick={() => runAction("start", async () => {
                            await launchServer(server.template, true);
                        })} disabled={action !== null}>
                            Start
                        </Button>
                    )}
                    <Button type="secondary" onClick={() => runAction("restart", async () => {
                        await restartServer(server.name);
                    })} disabled={action !== null}>
                        Neustart
                    </Button>
                    <Button type="danger" onClick={() => runAction("stop", async () => {
                        await stopServer(server.name);
                    })} disabled={action !== null}>
                        Stop
                    </Button>
                </div>
                {actionError && <p role="alert">{actionError}</p>}
                {metricsError && <p role="alert">{metricsError}</p>}
                <LineChart
                    data={playerCountData}
                    keyName={t("metrics.online")}
                    title={t("metrics.playercount")}
                    width={350}
                    height={175}
                />
            </div>
        </div>
    );
}

export default ServerRoute;
