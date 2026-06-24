import {useEffect, useState} from "react";
import {
    getServerPlayerCountData,
    localizeMetricKeys,
    type PlayerCountData
} from "../lib/api.ts";
import {LIVE_METRIC_EVENT, type LiveMetricMessage} from "../lib/LiveConsoleConnection.ts";

type ServerMetrics = {
    playerCountData: PlayerCountData[];
    error: string | null;
}

export function useServerMetrics(name: string | null): ServerMetrics {
    const [playerCountData, setPlayerCountData] = useState<PlayerCountData[]>([]);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (name === null) return;

        let cancelled = false;

        getServerPlayerCountData(name)
            .then((playerCount) => {
                if (cancelled) return;

                setPlayerCountData(playerCount);
            })
            .catch((reason: unknown) => {
                if (cancelled) return;

                setError(reason instanceof Error ? reason.message : "Failed to load server metrics");
            });

        return () => {
            cancelled = true;
        };
    }, [name]);

    useEffect(() => {
        if (name === null) return;

        function handleMetrics(event: Event) {
            const message = (event as CustomEvent<LiveMetricMessage>).detail;
            if (message.console !== `server-${name}`) return;
            setPlayerCountData(localizeMetricKeys(message.playerCount, "minutes"));
        }

        window.addEventListener(LIVE_METRIC_EVENT, handleMetrics);
        return () => window.removeEventListener(LIVE_METRIC_EVENT, handleMetrics);
    }, [name]);

    return { playerCountData, error };
}
