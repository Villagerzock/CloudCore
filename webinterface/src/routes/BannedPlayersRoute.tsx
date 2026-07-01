import styles from "./BannedPlayersRoute.module.css";
import {type FormEvent, useEffect, useState} from "react";
import {FaPlus, FaTimes, FaTrash} from "react-icons/fa";
import Button from "../components/Button.tsx";
import {
    createBan,
    deleteBan,
    getBannedPlayers,
    updateBan,
    type BannedPlayer
} from "../lib/api.ts";
import {useNodePermissions} from "../hooks/useNodePermissions.ts";
import {useI18n} from "../lib/i18n.ts";
import {useToast} from "../components/ToastProvider.tsx";
import {errorMessage} from "../lib/errors.ts";

function formatDateTime(value: string | null, fallback: string): string {
    if (!value) return fallback;
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(new Date(value));
}

function playerLabel(ban: BannedPlayer): string {
    return ban.name?.trim() || ban.uuid;
}

function parseDurationMillis(value: string): number | null | undefined {
    const normalized = value.trim().toLowerCase();
    if (!normalized || normalized === "permanent" || normalized === "perm") {
        return null;
    }

    const units: Record<string, number> = {
        m: 60 * 1000,
        h: 60 * 60 * 1000,
        d: 24 * 60 * 60 * 1000,
        w: 7 * 24 * 60 * 60 * 1000
    };
    let remaining = normalized;
    let total = 0;
    for (const match of normalized.matchAll(/(\d+)\s*([mhdw])/g)) {
        total += Number(match[1]) * units[match[2]];
        remaining = remaining.replace(match[0], "");
    }
    return total > 0 && remaining.trim() === "" ? total : undefined;
}

function expiresAtFromDuration(value: string, baseValue?: string): string | null {
    const duration = parseDurationMillis(value);
    if (duration === undefined) {
        throw new Error("Invalid duration");
    }
    if (duration === null) {
        return null;
    }
    const base = baseValue ? new Date(baseValue) : new Date();
    return new Date(base.getTime() + duration).toISOString();
}

function durationFromBan(ban: BannedPlayer): string {
    if (!ban.expiresAt) {
        return "permanent";
    }
    const duration = Math.max(0, new Date(ban.expiresAt).getTime() - new Date(ban.bannedAt).getTime());
    const units: Array<[string, number]> = [
        ["w", 7 * 24 * 60 * 60 * 1000],
        ["d", 24 * 60 * 60 * 1000],
        ["h", 60 * 60 * 1000],
        ["m", 60 * 1000]
    ];
    for (const [unit, size] of units) {
        if (duration >= size && duration % size === 0) {
            return `${duration / size}${unit}`;
        }
    }
    return `${Math.max(1, Math.round(duration / (60 * 1000)))}m`;
}

function BannedPlayersRoute(){
    const {t} = useI18n();
    const {showToast, showError} = useToast();
    const permissions = useNodePermissions();
    const [bans, setBans] = useState<BannedPlayer[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [adding, setAdding] = useState(false);
    const [expandedUuid, setExpandedUuid] = useState<string | null>(null);
    const [newPlayer, setNewPlayer] = useState("");
    const [newReason, setNewReason] = useState("");
    const [newDuration, setNewDuration] = useState("");
    const [editedReason, setEditedReason] = useState("");
    const [editedDuration, setEditedDuration] = useState("");
    const [savingNew, setSavingNew] = useState(false);
    const [savingExisting, setSavingExisting] = useState(false);

    useEffect(() => {
        setLoading(true);
        getBannedPlayers()
            .then(loadedBans => {
                setBans(loadedBans);
                setError(null);
            })
            .catch(reason => {
                const message = errorMessage(reason, "Failed to load bans");
                setError(message);
                showError(message);
            })
            .finally(() => setLoading(false));
    }, [showError]);

    const loadedBans = bans;
    const canAdd = permissions.has("BANNED_PLAYERS_ADD");
    const canEdit = permissions.has("BANNED_PLAYERS_EDIT");

    function toggleBan(ban: BannedPlayer) {
        if (!canEdit) return;
        setAdding(false);
        setError(null);
        if (expandedUuid === ban.uuid) {
            setExpandedUuid(null);
            return;
        }
        setExpandedUuid(ban.uuid);
        setEditedReason(ban.reason);
        setEditedDuration(durationFromBan(ban));
    }

    async function handleCreateBan(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (savingNew) return;

        try {
            setSavingNew(true);
            const createdBan = await createBan(newPlayer, newReason, expiresAtFromDuration(newDuration));
            setBans([createdBan, ...loadedBans.filter(ban => ban.uuid !== createdBan.uuid)]);
            setNewPlayer("");
            setNewReason("");
            setNewDuration("");
            setAdding(false);
            setError(null);
            showToast(t("bans.added"));
        } catch (reason) {
            const message = reason instanceof Error && reason.message === "Invalid duration" ? t("bans.invalid_duration") : errorMessage(reason, "Failed to add ban");
            setError(message);
            showError(message);
        } finally {
            setSavingNew(false);
        }
    }

    async function handleUpdateBan(event: FormEvent<HTMLFormElement>, ban: BannedPlayer) {
        event.preventDefault();
        if (savingExisting) return;

        try {
            setSavingExisting(true);
            const updatedBan = await updateBan(ban.uuid, editedReason, expiresAtFromDuration(editedDuration, ban.bannedAt));
            setBans(loadedBans.map(candidate => candidate.uuid === updatedBan.uuid ? updatedBan : candidate));
            setExpandedUuid(null);
            setError(null);
            showToast(t("bans.saved"));
        } catch (reason) {
            const message = reason instanceof Error && reason.message === "Invalid duration" ? t("bans.invalid_duration") : errorMessage(reason, "Failed to save ban");
            setError(message);
            showError(message);
        } finally {
            setSavingExisting(false);
        }
    }

    async function handleDeleteBan(ban: BannedPlayer) {
        if (savingExisting) return;
        if (!window.confirm(t("confirm.unban").replace("{name}", playerLabel(ban)))) {
            return;
        }

        try {
            setSavingExisting(true);
            await deleteBan(ban.uuid);
            setBans(loadedBans.filter(candidate => candidate.uuid !== ban.uuid));
            setExpandedUuid(null);
            setError(null);
            showToast(t("bans.unbanned"));
        } catch (reason) {
            const message = errorMessage(reason, "Failed to delete ban");
            setError(message);
            showError(message);
        } finally {
            setSavingExisting(false);
        }
    }

    return (
        <div className={styles.scrollContainer}>
            {error && <p className={styles.error} role="alert">{error}</p>}
            <div className={styles.list}>
                <div className={styles.listHeader}>
                    <h2>{t("page.banned_players")}</h2>
                    {canAdd && (
                        <Button
                            type={adding ? "secondary" : "primary"}
                            onClick={() => {
                                setExpandedUuid(null);
                                setAdding(current => !current);
                            }}
                        >
                            {adding ? <FaTimes aria-hidden/> : <FaPlus aria-hidden/>}
                            <span>{adding ? t("action.cancel") : t("bans.add")}</span>
                        </Button>
                    )}
                </div>
                {loading && <p className={styles.empty}>{t("state.loading")}</p>}
                {adding && (
                    <form className={styles.addForm} onSubmit={handleCreateBan}>
                        <label className={styles.field}>
                            <span>{t("bans.player")}</span>
                            <input
                                value={newPlayer}
                                onChange={event => setNewPlayer(event.target.value)}
                                placeholder={t("maintenance.player_placeholder")}
                                required
                            />
                        </label>
                        <label className={styles.field}>
                            <span>{t("bans.reason")}</span>
                            <input
                                value={newReason}
                                onChange={event => setNewReason(event.target.value)}
                                maxLength={300}
                                required
                            />
                        </label>
                        <label className={styles.field}>
                            <span>{t("bans.duration")}</span>
                            <input
                                value={newDuration}
                                onChange={event => setNewDuration(event.target.value)}
                                placeholder={t("bans.duration_placeholder")}
                            />
                        </label>
                        <Button type="primary" buttonType="submit" disabled={savingNew}>
                            {t("action.add")}
                        </Button>
                    </form>
                )}
                {!loading && loadedBans.length === 0 && !error && <p className={styles.empty}>{t("bans.empty")}</p>}
                {loadedBans.map(ban => (
                    <div className={styles.itemShell} key={ban.uuid}>
                        <div
                            className={`${styles.card} ${expandedUuid === ban.uuid ? styles.expanded : ""} ${!canEdit ? styles.readonly : ""}`}
                            onClick={canEdit ? () => toggleBan(ban) : undefined}
                            role={canEdit ? "button" : undefined}
                            tabIndex={canEdit ? 0 : undefined}
                            onKeyDown={event => {
                                if (event.key === "Enter" || event.key === " ") {
                                    event.preventDefault();
                                    toggleBan(ban);
                                }
                            }}
                        >
                            <h2>{playerLabel(ban)}</h2>
                            <p>{formatDateTime(ban.expiresAt, t("bans.permanent"))} • {durationFromBan(ban)} • {ban.reason}</p>
                        </div>
                        {expandedUuid === ban.uuid && (
                            <form className={styles.editForm} onSubmit={event => handleUpdateBan(event, ban)}>
                                <label className={styles.field}>
                                    <span>{t("bans.reason")}</span>
                                    <input
                                        value={editedReason}
                                        onChange={event => setEditedReason(event.target.value)}
                                        maxLength={300}
                                        required
                                    />
                                </label>
                                <label className={styles.field}>
                                    <span>{t("bans.duration")}</span>
                                    <input
                                        value={editedDuration}
                                        onChange={event => setEditedDuration(event.target.value)}
                                        placeholder={t("bans.duration_placeholder")}
                                    />
                                </label>
                                <div className={styles.formActions}>
                                    <Button type="secondary" onClick={() => handleDeleteBan(ban)} disabled={savingExisting}>
                                        <FaTrash aria-hidden/>
                                        <span>{t("bans.unban")}</span>
                                    </Button>
                                    <Button type="secondary" onClick={() => setExpandedUuid(null)}>{t("action.cancel")}</Button>
                                    <Button type="primary" buttonType="submit" disabled={savingExisting}>{t("action.save")}</Button>
                                </div>
                            </form>
                        )}
                    </div>
                ))}
            </div>
        </div>
    )
}
export default BannedPlayersRoute;
