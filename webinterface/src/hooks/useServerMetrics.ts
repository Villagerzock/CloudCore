import {useEffect, useState} from "react";
import {
    getServerNetworkData,
    getServerPlayerCountData,
    type NetworkData,
    type PlayerCountData
} from "../lib/api.ts";

type ServerMetrics = {
    playerCountData: PlayerCountData[];
    networkData: NetworkData[];
    error: string | null;
}

export function useServerMetrics(name: string | null): ServerMetrics {
    const [playerCountData, setPlayerCountData] = useState<PlayerCountData[]>([]);
    const [networkData, setNetworkData] = useState<NetworkData[]>([]);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (name === null) return;

        let cancelled = false;

        Promise.all([getServerPlayerCountData(name), getServerNetworkData(name)])
            .then(([playerCount, network]) => {
                if (cancelled) return;

                setPlayerCountData(playerCount);
                setNetworkData(network);
            })
            .catch((reason: unknown) => {
                if (cancelled) return;

                setError(reason instanceof Error ? reason.message : "Failed to load server metrics");
            });

        return () => {
            cancelled = true;
        };
    }, [name]);

    return { playerCountData, networkData, error };
}
