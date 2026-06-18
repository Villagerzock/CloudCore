import {useEffect, useState} from "react";
import {
    getProxyNetworkData,
    getProxyPlayerCountData,
    type NetworkData,
    type PlayerCountData
} from "../lib/api.ts";

type ProxyMetrics = {
    playerCountData: PlayerCountData[];
    networkData: NetworkData[];
    error: string | null;
}

export function useProxyMetrics(): ProxyMetrics {
    const [playerCountData, setPlayerCountData] = useState<PlayerCountData[]>([]);
    const [networkData, setNetworkData] = useState<NetworkData[]>([]);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;

        Promise.all([getProxyPlayerCountData(), getProxyNetworkData()])
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
    }, []);

    return { playerCountData, networkData, error };
}
