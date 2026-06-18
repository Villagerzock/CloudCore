import {useEffect, useState} from "react";
import {useParams} from "react-router";
import LineChart from "../components/LineChart.tsx";
import LiveConsole from "../components/LiveConsole.tsx";
import {useServerMetrics} from "../hooks/useServerMetrics.ts";
import {getServerById, type Server} from "../lib/api.ts";
import styles from "./ServerRoute.module.css";

function ServerRoute(){
    const { id } = useParams<{ id: string }>();
    const serverId = id && /^\d+$/.test(id) ? Number(id) : null;
    const { playerCountData, networkData, error: metricsError } = useServerMetrics(serverId);
    const [server, setServer] = useState<Server | null>(null);
    const [serverError, setServerError] = useState<string | null>(null);

    useEffect(() => {
        if (serverId === null) return;

        let cancelled = false;
        setServer(null);
        setServerError(null);

        getServerById(serverId)
            .then((result) => {
                if (!cancelled) setServer(result);
            })
            .catch((reason: unknown) => {
                if (cancelled) return;
                setServerError(reason instanceof Error ? reason.message : "Failed to load server");
            });

        return () => {
            cancelled = true;
        };
    }, [serverId]);

    if (serverId === null) {
        return <p role="alert">Invalid server ID</p>;
    }

    if (serverError) {
        return <p role="alert">{serverError}</p>;
    }

    if (!server) {
        return <p>Loading server...</p>;
    }

    return (
        <div className={styles.server}>
            <div className={styles.console}>
                <LiveConsole consoleId={server.name}/>
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
                <LineChart
                    data={networkData}
                    dataKey="inbound"
                    keyName="Inbound"
                    additionalLines={[
                        {
                            dataKey: "outbound",
                            name: "Outbound",
                            color: "#22c55e"
                        }
                    ]}
                    title="Network"
                    width={350}
                    height={175}
                />
            </div>
        </div>
    );
}

export default ServerRoute;
