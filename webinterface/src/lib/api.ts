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

export type PlayerMetricRange = "days" | "hours";
export type NetworkMetricRange = "days" | "minutes";
type MetricRange = PlayerMetricRange | NetworkMetricRange;

export type Server = {
    name: string;
    template: string;
    online: number;
    max: number;
}

export type ServerTemplate = {
    name: string;
    server_software: string;
    version: string;
}

export type AuthResponse = {
    token: string;
    expiresAt: string;
    username: string;
}

export type Node = {
    id: number;
    serverId: string;
    name: string;
    ipAddress: string;
    linkedAt: string;
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "/api").replace(/\/$/, "");

async function getErrorMessage(response: Response, fallback: string): Promise<string> {
    const body = await response.json().catch(() => null) as {
        detail?: string;
        message?: string;
    } | null;
    return body?.detail ?? body?.message ?? fallback;
}

async function postAuth(path: string, body: object): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE_URL}${path}`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Accept: "application/json"
        },
        body: JSON.stringify(body)
    });

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, `Request failed (${response.status})`));
    }

    return response.json() as Promise<AuthResponse>;
}

export function login(username: string, password: string): Promise<AuthResponse> {
    return postAuth("/auth/login", { username: username.trim(), password });
}

export function register(username: string, email: string, password: string): Promise<AuthResponse> {
    return postAuth("/auth/register", {
        username: username.trim(),
        email: email.trim(),
        password
    });
}

export async function logout(): Promise<void> {
    const token = localStorage.getItem("auth_token");
    try {
        if (!token) return;

        const response = await fetch(`${API_BASE_URL}/auth/logout`, {
            method: "POST",
            headers: {
                Accept: "application/json",
                Authorization: `Bearer ${token}`
            }
        });
        if (!response.ok && response.status !== 401) {
            throw new Error(await getErrorMessage(response, `Logout failed (${response.status})`));
        }
    } finally {
        localStorage.removeItem("auth_token");
    }
}

function appendSelectedNode(path: string): string {
    const rawNodeId = new URLSearchParams(window.location.search).get("node");
    const nodeId = rawNodeId === null ? NaN : Number(rawNodeId);
    if (!Number.isSafeInteger(nodeId) || nodeId <= 0) {
        throw new Error("A valid node must be selected");
    }

    const separator = path.includes("?") ? "&" : "?";
    return `${path}${separator}node=${nodeId}`;
}

async function getJson<T>(path: string, includeSelectedNode = true): Promise<T> {
    const token = localStorage.getItem("auth_token");
    const requestPath = includeSelectedNode ? appendSelectedNode(path) : path;
    const response = await fetch(`${API_BASE_URL}${requestPath}`, {
        headers: {
            Accept: "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
    });

    if (response.status === 401) {
        localStorage.removeItem("auth_token");
        window.location.assign("/login");
        throw new Error("Authentication required");
    }

    if (!response.ok) {
        throw new Error(`API request failed (${response.status} ${response.statusText})`);
    }

    return response.json() as Promise<T>;
}

export function getNodes(): Promise<Node[]> {
    return getJson("/nodes", false);
}

export function getProxyPlayerCountData(range: PlayerMetricRange): Promise<PlayerCountData[]> {
    return getJson<PlayerCountData[]>(`/proxy/metrics/player-count?range=${range}`)
        .then(points => localizeMetricKeys(points, range));
}

export function getProxyNetworkData(range: NetworkMetricRange): Promise<NetworkData[]> {
    return getJson<NetworkData[]>(`/proxy/metrics/network?range=${range}`)
        .then(points => localizeMetricKeys(points, range));
}

export function getRunningServers(): Promise<Server[]> {
    return getJson("/servers");
}

export function getServerByName(name: string): Promise<Server> {
    return getJson(`/servers/${encodeURIComponent(name)}`);
}

export function getServerPlayerCountData(name: string): Promise<PlayerCountData[]> {
    return getJson<PlayerCountData[]>(`/servers/${encodeURIComponent(name)}/metrics/player-count`)
        .then(points => localizeMetricKeys(points, "minutes"));
}

export function getServerNetworkData(name: string): Promise<NetworkData[]> {
    return getJson(`/servers/${encodeURIComponent(name)}/metrics/network`);
}

export function getServerTemplates(): Promise<ServerTemplate[]> {
    return getJson("/templates");
}

function localizeMetricKeys<T extends ChartData>(points: T[], range: MetricRange): T[] {
    const options: Intl.DateTimeFormatOptions = range === "days"
        ? {day: "2-digit", month: "2-digit", year: "numeric"}
        : {hour: "2-digit", minute: "2-digit", hourCycle: "h23"};
    const formatter = new Intl.DateTimeFormat(undefined, options);

    return points.map(point => {
        const instant = new Date(point.key);
        return {
            ...point,
            key: Number.isNaN(instant.getTime()) ? point.key : formatter.format(instant)
        };
    });
}
