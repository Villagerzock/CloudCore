import {useEffect, useState} from "react";
import {useParams} from "react-router";
import LineChart from "../components/LineChart.tsx";
import LiveConsole from "../components/LiveConsole.tsx";
import {useServerMetrics} from "../hooks/useServerMetrics.ts";
import {getServerByName, type Server} from "../lib/api.ts";
import styles from "./ServerRoute.module.css";

function ServerRoute(){
    const { name } = useParams<{ name: string }>();
    const serverName = name?.trim() || null;
    const { playerCountData, error: metricsError } = useServerMetrics(serverName);
    const [result, setResult] = useState<{
        name: string;
        server: Server | null;
        error: string | null;
    } | null>(null);

    useEffect(() => {
        if (serverName === null) return;

        let cancelled = false;

        getServerByName(serverName)
            .then((server) => {
                if (!cancelled) setResult({name: serverName, server, error: null});
            })
            .catch((reason: unknown) => {
                if (cancelled) return;
                setResult({
                    name: serverName,
                    server: null,
                    error: reason instanceof Error ? reason.message : "Failed to load server"
                });
            });

        return () => {
            cancelled = true;
        };
    }, [serverName]);

    if (serverName === null) {
        return <p role="alert">Invalid server name</p>;
    }

    const currentResult = result?.name === serverName ? result : null;

    if (currentResult?.error) {
        return <p role="alert">{currentResult.error}</p>;
    }

    if (!currentResult?.server) {
        return <p>Loading server...</p>;
    }

    const server = currentResult.server;

    return (
        <div className={styles.server}>
            <div className={styles.console}>
                <LiveConsole consoleId={`server-${server.name}`}/>
            </div>
            <div className={styles.stats}>
                <h2>{server.name}</h2>
                <span>Template: {server.template}</span>
                <span>Online: {server.online}/{server.max}</span>
                {metricsError && <p role="alert">{metricsError}</p>}
                <LineChart
                    data={playerCountData}
                    keyName="Online"
                    title="Playercount"
                    width={350}
                    height={175}
                />
            </div>
        </div>
    );
}

export default ServerRoute;
