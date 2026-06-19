import {useEffect, useState} from "react";
import {
    getServerPlayerCountData,
    type PlayerCountData
} from "../lib/api.ts";

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

    return { playerCountData, error };
}
