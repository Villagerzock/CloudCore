import {useEffect, useMemo, useState} from "react";
import type {FormEvent} from "react";
import {useParams} from "react-router";
import {getRoles, getUser, updateUserRole, usePersistentNavigate, type Role, type User} from "../lib/api.ts";
import styles from "./ConfigRoute.module.css";
import Dropdown from "../components/Dropdown.tsx";
import {useToast} from "../components/ToastProvider.tsx";
import Button from "../components/Button.tsx";
import {useI18n} from "../lib/i18n.ts";

function UserConfigRoute() {
    const {id} = useParams();
    const navigate = usePersistentNavigate();
    const {showToast} = useToast();
    const {t} = useI18n();
    const userId = Number(id);
    const [user, setUser] = useState<User | null>(null);
    const [roles, setRoles] = useState<Role[]>([]);
    const [roleId, setRoleId] = useState<number | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (!Number.isSafeInteger(userId) || userId <= 0) {
            setError("Invalid user");
            return;
        }

        async function load() {
            const [loadedUser, loadedRoles] = await Promise.all([
                getUser(userId),
                getRoles()
            ]);
            setUser(loadedUser);
            setRoleId(loadedUser.roleId);
            setRoles(loadedRoles);
        }

        load().catch(reason => setError(reason instanceof Error ? reason.message : "Failed to load user"));
    }, [userId]);

    const unchanged = useMemo(() => user !== null && roleId === user.roleId, [roleId, user]);

    async function save(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (user === null || roleId === null || saving) return;

        try {
            setSaving(true);
            if (!unchanged) {
                await updateUserRole(user.id, roleId);
            }
            setError(null);
            showToast(unchanged ? "No changes to save" : "User saved");
            navigate("/users");
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "Failed to save user");
        } finally {
            setSaving(false);
        }
    }

    function cancel() {
        navigate("/users");
    }

    if (user === null) {
        return <div className={styles.page}>{error && <p className={styles.error}>{error}</p>}</div>;
    }

    return (
        <main className={styles.page}>
            <div className={styles.header}>
                <h1>{user.username}</h1>
                <p>{t("user.config")}</p>
            </div>
            <form className={styles.form} onSubmit={save}>
                <div className={styles.field}>
                    <label>{t("field.username")}</label>
                    <div className={styles.readonlyValue}>{user.username}</div>
                </div>
                <div className={styles.field}>
                    <label>{t("field.email")}</label>
                    <div className={styles.readonlyValue}>{user.email}</div>
                </div>
                <div className={styles.field}>
                    <label htmlFor="role">{t("field.role")}</label>
                    <Dropdown
                        id="role"
                        value={roleId}
                        options={roles.map(role => ({value: role.id, label: role.name}))}
                        onChange={setRoleId}
                    />
                </div>
                {error && <p className={styles.error}>{error}</p>}
                <div className={styles.actions}>
                    <Button type="secondary" onClick={cancel}>{t("action.cancel")}</Button>
                    <Button type="primary" buttonType="submit" disabled={saving}>{t("action.save")}</Button>
                </div>
            </form>
        </main>
    );
}

export default UserConfigRoute;
