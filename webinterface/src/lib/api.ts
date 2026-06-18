export type ChartData = {
    key: string;
    [dataKey: string]: string | number;
}

export type PlayerCountData = ChartData & {
    value: number;
}

export type NetworkData = ChartData & {
    inbound: number;
    outbound: number;
}

export type Server = {
    id: number;
    name: string;
    template: string;
    online: number;
    max: number;
}

export type ServerTemplate = {
    id: number;
    name: string;
    server_software: string;
    version: string;
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "/api").replace(/\/$/, "");

async function getJson<T>(path: string): Promise<T> {
    const response = await fetch(`${API_BASE_URL}${path}`, {
        headers: { Accept: "application/json" }
    });

    if (!response.ok) {
        throw new Error(`API request failed (${response.status} ${response.statusText})`);
    }

    return response.json() as Promise<T>;
}

export function getProxyPlayerCountData(): Promise<PlayerCountData[]> {
    return getJson("/proxy/metrics/player-count");
}

export function getProxyNetworkData(): Promise<NetworkData[]> {
    return getJson("/proxy/metrics/network");
}

export function getRunningServers(): Promise<Server[]> {
    return getJson("/servers");
}

export function getServerById(id: number): Promise<Server> {
    return getJson(`/servers/${id}`);
}

export function getServerPlayerCountData(id: number): Promise<PlayerCountData[]> {
    return getJson(`/servers/${id}/metrics/player-count`);
}

export function getServerNetworkData(id: number): Promise<NetworkData[]> {
    return getJson(`/servers/${id}/metrics/network`);
}

export function getServerTemplates(): Promise<ServerTemplate[]> {
    return getJson("/templates");
}
