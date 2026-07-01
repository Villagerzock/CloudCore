import {type NavigateFunction, type NavigateOptions, type Path, type To, useLocation, useNavigate} from "react-router";

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

export type User = {
    id : number;
    username : string;
    email: string;
    role : string;
    roleId : number;
    hasAsterix: boolean;
}

export type Role = {
    id : number;
    name : string;
    permissions : number;
    permissionOptions: Record<string, boolean>;
    permissionValues: Record<string, number>;
}

export type FileMimeType =
// Text
    | "text/plain"
    | "text/html"
    | "text/css"
    | "text/javascript"
    | "text/csv"
    | "text/xml"

    // Application
    | "application/json"
    | "application/xml"
    | "application/pdf"
    | "application/zip"
    | "application/gzip"
    | "application/javascript"
    | "application/octet-stream"
    | "application/x-yaml"

    // Images
    | "image/png"
    | "image/jpeg"
    | "image/gif"
    | "image/webp"
    | "image/svg+xml"
    | "image/bmp"
    | "image/x-icon"

    // Audio
    | "audio/mpeg"
    | "audio/ogg"
    | "audio/wav"
    | "audio/webm"

    // Video
    | "video/mp4"
    | "video/webm"
    | "video/ogg"
    | "video/x-msvideo"

    // Fonts
    | "font/ttf"
    | "font/otf"
    | "font/woff"
    | "font/woff2";

export interface FileResponse {
    isFile: true;
    type: FileMimeType;
    binary: boolean;
    contentUrl: string | null;
    tooLarge: boolean;
    sizeBytes: number;
    downloadUrl: string | null;
}

export type FileInFolder = {
    name : string;
    isFile: boolean;
}

export interface FolderResponse {
    isFile: false;
    files: FileInFolder[];
}

export type FileSystemResponse = FileResponse | FolderResponse;

export type PlayerMetricRange = "days" | "hours" | "minutes";
export type NetworkMetricRange = "days" | "minutes";
type MetricRange = PlayerMetricRange | NetworkMetricRange;

export type Server = {
    name: string;
    template: string;
    online: number;
    max: number;
    singleton: boolean;
}

export type ServerTemplate = {
    name: string;
    server_software: string;
    version: string;
}

export type CreateTemplateRequest = {
    name: string;
    server_software: string;
    version: string;
    memory: string;
    world_type: string;
    superflat_type?: string | null;
    seed?: string | null;
}

export type MatchmakingConfiguration = {
    name: string;
    template: string;
    max_amount_of_servers: number;
    max_players_per_server: number;
    players_per_team: number;
    can_rejoin: boolean;
    split_same_queue: boolean;
    single_queue_server_on_split: boolean;
    max_mmv_diff: number;
}

export type MaintenanceStatus = {
    active: boolean;
    players: Array<{
        uuid: string;
        name: string | null;
    }>;
}

export type BannedPlayer = {
    uuid: string;
    name: string | null;
    reason: string;
    bannedAt: string;
    expiresAt: string | null;
}

export type Account = {
    id: number;
    username: string;
    email: string;
    sshKeys: Array<{
        id: number;
        key: string;
    }>;
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

function redirectToLogin(): void {
    localStorage.removeItem("auth_token");
    localStorage.removeItem("auth_username");
    if (window.location.pathname !== "/login" && window.location.pathname !== "/register") {
        window.location.replace("/login");
    }
}

function waitForRedirect<T>(): Promise<T> {
    return new Promise(() => {});
}

function getAuthTokenOrRedirect<T>(): string | Promise<T> {
    const token = localStorage.getItem("auth_token");
    if (!token) {
        redirectToLogin();
        return waitForRedirect<T>();
    }
    return token;
}

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
        localStorage.removeItem("auth_username");
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
    const token = getAuthTokenOrRedirect<T>();
    if (typeof token !== "string") return token;
    const requestPath = includeSelectedNode ? appendSelectedNode(path) : path;
    const response = await fetch(`${API_BASE_URL}${requestPath}`, {
        headers: {
            Accept: "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
    });

    if (response.status === 401) {
        redirectToLogin();
        return waitForRedirect<T>();
    }

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, `API request failed (${response.status} ${response.statusText})`));
    }

    return response.json() as Promise<T>;
}

async function postJson<T>(path: string, body: object, includeSelectedNode = true): Promise<T> {
    const token = getAuthTokenOrRedirect<T>();
    if (typeof token !== "string") return token;
    const requestPath = includeSelectedNode ? appendSelectedNode(path) : path;
    const response = await fetch(`${API_BASE_URL}${requestPath}`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify(body)
    });

    if (response.status === 401) {
        redirectToLogin();
        return waitForRedirect<T>();
    }

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, `API request failed (${response.status} ${response.statusText})`));
    }

    return response.json() as Promise<T>;
}

async function postNoContent(path: string): Promise<void> {
    const token = getAuthTokenOrRedirect<void>();
    if (typeof token !== "string") return token;
    const response = await fetch(`${API_BASE_URL}${appendSelectedNode(path)}`, {
        method: "POST",
        headers: {
            Accept: "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
    });

    if (response.status === 401) {
        redirectToLogin();
        return waitForRedirect<void>();
    }

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, `API request failed (${response.status} ${response.statusText})`));
    }
}

async function patchJson<T>(path: string, body: object, includeSelectedNode = true): Promise<T> {
    const token = getAuthTokenOrRedirect<T>();
    if (typeof token !== "string") return token;
    const requestPath = includeSelectedNode ? appendSelectedNode(path) : path;
    const response = await fetch(`${API_BASE_URL}${requestPath}`, {
        method: "PATCH",
        headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify(body)
    });

    if (response.status === 401) {
        redirectToLogin();
        return waitForRedirect<T>();
    }

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, `API request failed (${response.status} ${response.statusText})`));
    }

    return response.json() as Promise<T>;
}

async function deleteRequest(path: string, includeSelectedNode = true): Promise<void> {
    const token = getAuthTokenOrRedirect<void>();
    if (typeof token !== "string") return token;
    const requestPath = includeSelectedNode ? appendSelectedNode(path) : path;
    const response = await fetch(`${API_BASE_URL}${requestPath}`, {
        method: "DELETE",
        headers: {
            Accept: "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
    });

    if (response.status === 401) {
        redirectToLogin();
        return waitForRedirect<void>();
    }

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, `Delete failed (${response.status} ${response.statusText})`));
    }
}

async function deleteJson<T>(path: string, includeSelectedNode = true): Promise<T> {
    const token = getAuthTokenOrRedirect<T>();
    if (typeof token !== "string") return token;
    const requestPath = includeSelectedNode ? appendSelectedNode(path) : path;
    const response = await fetch(`${API_BASE_URL}${requestPath}`, {
        method: "DELETE",
        headers: {
            Accept: "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
    });

    if (response.status === 401) {
        redirectToLogin();
        return waitForRedirect<T>();
    }

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, `Delete failed (${response.status} ${response.statusText})`));
    }

    return response.json() as Promise<T>;
}

export function getNodes(): Promise<Node[]> {
    return getJson("/nodes", false);
}

export function getAccount(): Promise<Account> {
    return getJson("/account", false);
}

export function updateAccountProfile(username: string, email: string): Promise<Account> {
    return patchJson("/account", {
        username: username.trim(),
        email: email.trim()
    }, false);
}

export function updateAccountPassword(currentPassword: string, newPassword: string): Promise<Account> {
    return patchJson("/account/password", {
        currentPassword,
        newPassword
    }, false);
}

export function addSshKey(key: string): Promise<Account> {
    return postJson("/account/ssh-keys", {key: key.trim()}, false);
}

export function updateSshKey(keyId: number, key: string): Promise<Account> {
    return patchJson(`/account/ssh-keys/${keyId}`, {key: key.trim()}, false);
}

export function deleteSshKey(keyId: number): Promise<Account> {
    return deleteJson(`/account/ssh-keys/${keyId}`, false);
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

export function launchServer(template: string, singleton: boolean): Promise<string> {
    return postJson<{name: string}>("/servers", {
        template: template.trim(),
        singleton
    }).then(response => response.name);
}

export function getServerByName(name: string): Promise<Server> {
    return getJson(`/servers/${encodeURIComponent(name)}`);
}

export function stopServer(name: string): Promise<void> {
    return postNoContent(`/servers/${encodeURIComponent(name)}/stop`);
}

export function restartServer(name: string): Promise<string> {
    return postJson<{name: string}>(`/servers/${encodeURIComponent(name)}/restart`, {})
        .then(response => response.name);
}

export function startProxy(): Promise<void> {
    return postNoContent("/proxy/start");
}

export function stopProxy(): Promise<void> {
    return postNoContent("/proxy/stop");
}

export function restartProxy(): Promise<void> {
    return postNoContent("/proxy/restart");
}

export function getServerPlayerCountData(name: string): Promise<PlayerCountData[]> {
    return getJson<PlayerCountData[]>(`/servers/${encodeURIComponent(name)}/metrics/player-count`)
        .then(points => localizeMetricKeys(points, "minutes"));
}

export function getServerNetworkData(name: string): Promise<NetworkData[]> {
    return getJson(`/servers/${encodeURIComponent(name)}/metrics/network`);
}

export function getMatchmakingConfigurations(): Promise<MatchmakingConfiguration[]> {
    return getJson("/matchmaking");
}

export function getMatchmakingTemplates(): Promise<ServerTemplate[]> {
    return getJson("/matchmaking/templates");
}

export function createMatchmakingConfiguration(
    configuration: MatchmakingConfiguration
): Promise<MatchmakingConfiguration> {
    return postJson("/matchmaking", configuration);
}

export function updateMatchmakingConfiguration(
    name: string,
    configuration: MatchmakingConfiguration
): Promise<MatchmakingConfiguration> {
    return patchJson(`/matchmaking/${encodeURIComponent(name)}`, configuration);
}

export function deleteMatchmakingConfiguration(name: string): Promise<void> {
    return deleteRequest(`/matchmaking/${encodeURIComponent(name)}`);
}

export function getMaintenanceStatus(): Promise<MaintenanceStatus> {
    return getJson("/maintenance");
}

export function setMaintenanceActive(active: boolean): Promise<MaintenanceStatus> {
    return patchJson("/maintenance", {active});
}

export function addMaintenancePlayer(player: string): Promise<MaintenanceStatus> {
    return postJson("/maintenance/players", {player: player.trim()});
}

export function removeMaintenancePlayer(uuid: string): Promise<MaintenanceStatus> {
    return deleteJson(`/maintenance/players/${encodeURIComponent(uuid)}`);
}

export function getBannedPlayers(): Promise<BannedPlayer[]> {
    return getJson("/bans");
}

export function createBan(player: string, reason: string, expiresAt: string | null): Promise<BannedPlayer> {
    return postJson("/bans", {
        player: player.trim(),
        reason: reason.trim(),
        expiresAt
    });
}

export function updateBan(uuid: string, reason: string, expiresAt: string | null): Promise<BannedPlayer> {
    return patchJson(`/bans/${encodeURIComponent(uuid)}`, {
        reason: reason.trim(),
        expiresAt
    });
}

export function deleteBan(uuid: string): Promise<void> {
    return deleteRequest(`/bans/${encodeURIComponent(uuid)}`);
}

export function getServerTemplates(): Promise<ServerTemplate[]> {
    return getJson("/templates");
}

export function createTemplate(request: CreateTemplateRequest): Promise<ServerTemplate> {
    return postJson("/templates", request);
}
export function getServerTemplateFileSystem(template: string,path: string): Promise<FileSystemResponse> {
    return getJson(
        `/templates/${template}/` +
        path
            .split("/")
            .map(encodeURIComponent)
            .join("/")
    );
}

export async function downloadTemplateFile(downloadUrl: string, fileName: string): Promise<void> {
    const token = getAuthTokenOrRedirect<void>();
    if (typeof token !== "string") return token;
    const apiPath = downloadUrl.startsWith("/api") ? downloadUrl.slice(4) : downloadUrl;
    const requestPath = apiPath.includes("?node=") || apiPath.includes("&node=")
        ? apiPath
        : appendSelectedNode(apiPath);
    const response = await fetch(`${API_BASE_URL}${requestPath}`, {
        headers: {
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
    });

    if (!response.ok) {
        if (response.status === 401) {
            redirectToLogin();
            return waitForRedirect<void>();
        }
        throw new Error(await getErrorMessage(response, `Download failed (${response.status} ${response.statusText})`));
    }

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

export async function getTemplateFileContent(contentUrl: string): Promise<string> {
    const token = getAuthTokenOrRedirect<string>();
    if (typeof token !== "string") return token;
    const apiPath = contentUrl.startsWith("/api") ? contentUrl.slice(4) : contentUrl;
    const requestPath = apiPath.includes("?node=") || apiPath.includes("&node=")
        ? apiPath
        : appendSelectedNode(apiPath);
    const response = await fetch(`${API_BASE_URL}${requestPath}`, {
        headers: {
            Accept: "text/plain,*/*",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
    });

    if (response.status === 401) {
        redirectToLogin();
        return waitForRedirect<string>();
    }

    if (!response.ok) {
        throw new Error(await getErrorMessage(response, `Content failed (${response.status} ${response.statusText})`));
    }

    return response.text();
}

export function downloadTemplateFilePath(template: string, path: string, fileName: string): Promise<void> {
    return downloadTemplateFile(
        `/api/templates/${encodeURIComponent(template)}/download/` +
        path
            .split("/")
            .map(encodeURIComponent)
            .join("/"),
        fileName
    );
}

export function saveTemplateFile(template: string, path: string, content: string): Promise<FileSystemResponse> {
    return patchJson(
        `/templates/${encodeURIComponent(template)}/` +
        path
            .split("/")
            .map(encodeURIComponent)
            .join("/"),
        {binary: false, content}
    );
}

export function deleteTemplatePath(template: string, path: string): Promise<void> {
    return deleteRequest(
        `/templates/${encodeURIComponent(template)}/` +
        path
            .split("/")
            .map(encodeURIComponent)
            .join("/")
    );
}

export function createTemplateFolder(template: string, folderPath: string, folderName: string): Promise<FileSystemResponse> {
    return postJson(
        `/templates/${encodeURIComponent(template)}/folders/` +
        folderPath
            .split("/")
            .map(encodeURIComponent)
            .join("/"),
        {path: folderName}
    );
}

export function copyTemplatePath(template: string, sourcePath: string, destinationFolderPath: string): Promise<FileSystemResponse> {
    return postJson(
        `/templates/${encodeURIComponent(template)}/copy/` +
        sourcePath
            .split("/")
            .map(encodeURIComponent)
            .join("/"),
        {path: destinationFolderPath}
    );
}

export function moveTemplatePath(template: string, sourcePath: string, destinationFolderPath: string): Promise<FileSystemResponse> {
    return postJson(
        `/templates/${encodeURIComponent(template)}/move/` +
        sourcePath
            .split("/")
            .map(encodeURIComponent)
            .join("/"),
        {path: destinationFolderPath}
    );
}

export function renameTemplatePath(template: string, sourcePath: string, newName: string): Promise<FileSystemResponse> {
    return postJson(
        `/templates/${encodeURIComponent(template)}/rename/` +
        sourcePath
            .split("/")
            .map(encodeURIComponent)
            .join("/"),
        {path: newName}
    );
}

export function uploadTemplateFile(
    template: string,
    folderPath: string,
    file: File,
    relativePath: string
): Promise<FileSystemResponse> {
    const token = getAuthTokenOrRedirect<FileSystemResponse>();
    if (typeof token !== "string") return token;
    const form = new FormData();
    form.append("file", file);
    form.append("relativePath", relativePath);

    return fetch(`${API_BASE_URL}${appendSelectedNode(
        `/templates/${encodeURIComponent(template)}/upload/` +
        folderPath
            .split("/")
            .map(encodeURIComponent)
            .join("/")
    )}`, {
        method: "POST",
        headers: {
            Accept: "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: form
    }).then(async response => {
        if (response.status === 401) {
            redirectToLogin();
            return waitForRedirect<FileSystemResponse>();
        }
        if (!response.ok) {
            throw new Error(await getErrorMessage(response, `Upload failed (${response.status} ${response.statusText})`));
        }
        return response.json() as Promise<FileSystemResponse>;
    });
}

export function getUsers(): Promise<User[]>{
    return getJson("/users")
}

export function getUser(id: number): Promise<User> {
    return getJson(`/users/${id}`);
}

export function createUser(email: string, roleId: number): Promise<User> {
    return postJson("/users", {email: email.trim(), roleId});
}

export function updateUserRole(id: number, roleId: number): Promise<User> {
    return patchJson(`/users/${id}`, {roleId});
}

export function getMe(): Promise<User> {
    return getJson("/me");
}

export function getRoles(): Promise<Role[]> {
    return getJson("/roles");
}

export function getRole(id: number): Promise<Role> {
    return getJson(`/roles/${id}`);
}

export function createRole(name: string, permissions: number): Promise<Role> {
    return postJson("/roles", {name: name.trim(), permissions});
}

export function updateRole(id: number, changes: {name?: string; permissions?: Record<string, boolean>}): Promise<Role> {
    return patchJson(`/roles/${id}`, changes);
}

export function moveRole(roleId: number, afterRoleId: number): Promise<Role[]> {
    return patchJson("/roles/order", {roleId, afterRoleId});
}

export function localizeMetricKeys<T extends ChartData>(points: T[], range: MetricRange): T[] {
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

export function usePersistentNavigate(): NavigateFunction{
    const navigate = useNavigate();
    const location = useLocation();

    return (toOrDelta : To | number, options? : NavigateOptions) => {

        if (typeof toOrDelta == "number") return;
        const to : To = toOrDelta;

        const locationParams = new URLSearchParams(location.search);

        const actualTo : Partial<Path> = typeof to == "string" ? {pathname: to} : to;

        if (locationParams.has("node")){
            const resultParams = new URLSearchParams(actualTo.search);
            resultParams.set("node",locationParams.get("node") ?? "");
            actualTo.search = resultParams.toString();
            console.log("Set node")
        }

        console.log(`Navigating to: ${actualTo}`);

        navigate(actualTo, options);
    }
}
