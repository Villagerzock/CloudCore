import {useEffect, useState} from "react";
import {
    getProxyNetworkData,
    getProxyPlayerCountData,
    localizeMetricKeys,
    type NetworkMetricRange,
    type PlayerMetricRange,
    type NetworkData,
    type PlayerCountData
} from "../lib/api.ts";
import {LIVE_METRIC_EVENT, type LiveMetricMessage} from "../lib/LiveConsoleConnection.ts";

type ProxyMetrics = {
    playerCountData: PlayerCountData[];
    networkData: NetworkData[];
    error: string | null;
}

export function useProxyMetrics(
    playerRange: PlayerMetricRange,
    networkRange: NetworkMetricRange
): ProxyMetrics {
    const [playerCountData, setPlayerCountData] = useState<PlayerCountData[]>([]);
    const [networkData, setNetworkData] = useState<NetworkData[]>([]);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;

        Promise.all([
            getProxyPlayerCountData(playerRange),
            getProxyNetworkData(networkRange)
        ])
            .then(([playerCount, network]) => {
                if (cancelled) return;

                setPlayerCountData(playerCount);
                setNetworkData(network);
            })
            .catch((reason: unknown) => {
                if (cancelled) return;

                setError(reason instanceof Error ? reason.message : "Failed to load proxy metrics");
            });

        return () => {
            cancelled = true;
        };
    }, [networkRange, playerRange]);

    useEffect(() => {
        function handleMetrics(event: Event) {
            const message = (event as CustomEvent<LiveMetricMessage>).detail;
            if (message.console !== "proxy") return;
            setPlayerCountData(localizeMetricKeys(message.playerCount, playerRange));
            setNetworkData(localizeMetricKeys(message.network, networkRange));
        }

        window.addEventListener(LIVE_METRIC_EVENT, handleMetrics);
        return () => window.removeEventListener(LIVE_METRIC_EVENT, handleMetrics);
    }, [networkRange, playerRange]);

    return { playerCountData, networkData, error };
}
