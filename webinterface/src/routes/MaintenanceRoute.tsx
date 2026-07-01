import {useEffect, useState} from "react";
import {
    addMaintenancePlayer,
    getMaintenanceStatus,
    removeMaintenancePlayer,
    setMaintenanceActive,
    type MaintenanceStatus
} from "../lib/api.ts";
import type {FormEvent} from "react";
import Button from "../components/Button.tsx";
import {useI18n} from "../lib/i18n.ts";
import styles from "./MaintenanceRoute.module.css";
import {useToast} from "../components/ToastProvider.tsx";
import {errorMessage} from "../lib/errors.ts";

function MaintenanceRoute() {
    const {t} = useI18n();
    const {showError, showToast} = useToast();
    const [status, setStatus] = useState<MaintenanceStatus | null>(null);
    const [saving, setSaving] = useState(false);
    const [player, setPlayer] = useState("");
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        getMaintenanceStatus()
            .then(setStatus)
            .catch(reason => {
                const message = errorMessage(reason, t("error.load_failed"));
                setError(message);
                showError(message);
            });
    }, [showError, t]);

    async function toggle() {
        if (!status || saving) return;

        try {
            setSaving(true);
            setError(null);
            setStatus(await setMaintenanceActive(!status.active));
            showToast(t("state.saving"));
        } catch (reason) {
            const message = errorMessage(reason, t("error.save_failed"));
            setError(message);
            showError(message);
        } finally {
            setSaving(false);
        }
    }

    async function addPlayer(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (saving || player.trim().length === 0) return;

        try {
            setSaving(true);
            setError(null);
            setStatus(await addMaintenancePlayer(player));
            setPlayer("");
            showToast(t("action.add"));
        } catch (reason) {
            const message = errorMessage(reason, t("error.save_failed"));
            setError(message);
            showError(message);
        } finally {
            setSaving(false);
        }
    }

    async function removePlayer(uuid: string) {
        try {
            setSaving(true);
            setError(null);
            setStatus(await removeMaintenancePlayer(uuid));
            showToast(t("action.delete"));
        } catch (reason) {
            const message = errorMessage(reason, t("error.delete_failed"));
            setError(message);
            showError(message);
        } finally {
            setSaving(false);
        }
    }

    return (
        <main className={styles.page}>
            <header className={styles.header}>
                <div>
                    <h1>{t("page.maintenance")}</h1>
                    <p>{status?.active ? t("maintenance.active") : t("maintenance.inactive")}</p>
                </div>
                <Button type={status?.active ? "danger" : "primary"} onClick={toggle} disabled={!status || saving}>
                    {saving
                        ? t("state.saving")
                        : status?.active ? t("maintenance.turn_off") : t("maintenance.turn_on")}
                </Button>
            </header>
            {error && <p className={styles.error} role="alert">{error}</p>}
            <form className={styles.addForm} onSubmit={addPlayer}>
                <label className={styles.field}>
                    <span>{t("maintenance.player_input")}</span>
                    <input
                        value={player}
                        onChange={event => setPlayer(event.target.value)}
                        placeholder={t("maintenance.player_placeholder")}
                        required
                    />
                </label>
                <Button type="primary" buttonType="submit" disabled={saving || player.trim().length === 0}>
                    {t("action.add")}
                </Button>
            </form>
            <section className={styles.panel}>
                <div className={styles.tableHeader}>
                    <span>{t("field.username")}</span>
                    <span>UUID</span>
                    <span></span>
                </div>
                {status?.players.length === 0 && (
                    <p className={styles.empty}>{t("maintenance.empty")}</p>
                )}
                {status?.players.map(player => (
                    <div className={styles.row} key={player.uuid}>
                        <span>{player.name ?? t("maintenance.unknown_player")}</span>
                        <code>{player.uuid}</code>
                        <Button type="danger" onClick={() => removePlayer(player.uuid)} disabled={saving}>
                            {t("action.delete")}
                        </Button>
                    </div>
                ))}
            </section>
        </main>
    );
}

export default MaintenanceRoute;
