import {type FormEvent, useEffect, useState} from "react";
import {
    addSshKey,
    deleteSshKey,
    getAccount,
    updateAccountPassword,
    updateAccountProfile,
    updateSshKey,
    type Account
} from "../lib/api.ts";
import Button from "../components/Button.tsx";
import {useToast} from "../components/ToastProvider.tsx";
import {useI18n} from "../lib/i18n.ts";
import styles from "./AccountRoute.module.css";
import {FaPlus, FaTimes, FaTrash} from "react-icons/fa";
import {errorMessage} from "../lib/errors.ts";

function sshKeyInfo(key: string) {
    const parts = key.trim().split(/\s+/);
    return {
        type: parts[0] || "SSH",
        body: parts[1] || "",
        comment: parts.slice(2).join(" ")
    };
}

function AccountRoute() {
    const {t} = useI18n();
    const {showToast, showError} = useToast();
    const [account, setAccount] = useState<Account | null>(null);
    const [username, setUsername] = useState("");
    const [email, setEmail] = useState("");
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [newPasswordRepeat, setNewPasswordRepeat] = useState("");
    const [newSshKey, setNewSshKey] = useState("");
    const [editedSshKeys, setEditedSshKeys] = useState<Record<number, string>>({});
    const [error, setError] = useState<string | null>(null);
    const [savingProfile, setSavingProfile] = useState(false);
    const [savingPassword, setSavingPassword] = useState(false);
    const [savingSshKeyId, setSavingSshKeyId] = useState<number | null>(null);
    const [addingSshKey, setAddingSshKey] = useState(false);
    const [showAddSshKey, setShowAddSshKey] = useState(false);
    const [expandedSshKeyId, setExpandedSshKeyId] = useState<number | null>(null);

    useEffect(() => {
        getAccount()
            .then(loadedAccount => applyAccount(loadedAccount))
            .catch(reason => {
                const message = errorMessage(reason, "Failed to load account");
                setError(message);
                showError(message);
            });
    }, [showError]);

    function applyAccount(nextAccount: Account) {
        setAccount(nextAccount);
        setUsername(nextAccount.username);
        setEmail(nextAccount.email);
        setEditedSshKeys(Object.fromEntries(nextAccount.sshKeys.map(key => [key.id, key.key])));
        localStorage.setItem("auth_username", nextAccount.username);
        setError(null);
    }

    async function saveProfile(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (savingProfile) return;

        try {
            setSavingProfile(true);
            applyAccount(await updateAccountProfile(username, email));
            showToast(t("account.profile_saved"));
        } catch (reason) {
            const message = errorMessage(reason, "Failed to save profile");
            setError(message);
            showError(message);
        } finally {
            setSavingProfile(false);
        }
    }

    async function savePassword(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (savingPassword) return;
        if (newPassword !== newPasswordRepeat) {
            const message = t("account.password_mismatch");
            setError(message);
            showError(message);
            return;
        }

        try {
            setSavingPassword(true);
            applyAccount(await updateAccountPassword(currentPassword, newPassword));
            setCurrentPassword("");
            setNewPassword("");
            setNewPasswordRepeat("");
            showToast(t("account.password_saved"));
        } catch (reason) {
            const message = errorMessage(reason, "Failed to save password");
            setError(message);
            showError(message);
        } finally {
            setSavingPassword(false);
        }
    }

    async function handleAddSshKey(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (addingSshKey) return;

        try {
            setAddingSshKey(true);
            applyAccount(await addSshKey(newSshKey));
            setNewSshKey("");
            setShowAddSshKey(false);
            showToast(t("account.ssh_key_added"));
        } catch (reason) {
            const message = errorMessage(reason, "Failed to add SSH key");
            setError(message);
            showError(message);
        } finally {
            setAddingSshKey(false);
        }
    }

    async function handleUpdateSshKey(keyId: number) {
        if (savingSshKeyId !== null) return;

        try {
            setSavingSshKeyId(keyId);
            applyAccount(await updateSshKey(keyId, editedSshKeys[keyId] ?? ""));
            showToast(t("account.ssh_key_saved"));
        } catch (reason) {
            const message = errorMessage(reason, "Failed to save SSH key");
            setError(message);
            showError(message);
        } finally {
            setSavingSshKeyId(null);
        }
    }

    async function handleDeleteSshKey(keyId: number) {
        if (savingSshKeyId !== null) return;
        if (!window.confirm(t("confirm.delete").replace("{name}", t("account.ssh_key")))) {
            return;
        }

        try {
            setSavingSshKeyId(keyId);
            applyAccount(await deleteSshKey(keyId));
            showToast(t("account.ssh_key_deleted"));
        } catch (reason) {
            const message = errorMessage(reason, "Failed to delete SSH key");
            setError(message);
            showError(message);
        } finally {
            setSavingSshKeyId(null);
        }
    }

    if (!account) {
        return <main className={styles.page}>{error && <p className={styles.error}>{error}</p>}</main>;
    }

    return (
        <main className={styles.page}>
            <div className={styles.header}>
                <h1>{t("account.my_account")}</h1>
                <p>{account.username}</p>
            </div>
            {error && <p className={styles.error} role="alert">{error}</p>}

            <form className={styles.panel} onSubmit={saveProfile}>
                <h2>{t("account.profile")}</h2>
                <label className={styles.field}>
                    <span>{t("field.username")}</span>
                    <input value={username} onChange={event => setUsername(event.target.value)} required minLength={3} maxLength={50}/>
                </label>
                <label className={styles.field}>
                    <span>{t("field.email")}</span>
                    <input type="email" value={email} onChange={event => setEmail(event.target.value)} required maxLength={254}/>
                </label>
                <div className={styles.actions}>
                    <Button type="primary" buttonType="submit" disabled={savingProfile}>{t("action.save")}</Button>
                </div>
            </form>

            <form className={styles.panel} onSubmit={savePassword}>
                <h2>{t("account.password")}</h2>
                <label className={styles.field}>
                    <span>{t("account.current_password")}</span>
                    <input type="password" value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} required maxLength={128}/>
                </label>
                <label className={styles.field}>
                    <span>{t("account.new_password")}</span>
                    <input type="password" value={newPassword} onChange={event => setNewPassword(event.target.value)} required minLength={8} maxLength={128}/>
                </label>
                <label className={styles.field}>
                    <span>{t("account.repeat_password")}</span>
                    <input type="password" value={newPasswordRepeat} onChange={event => setNewPasswordRepeat(event.target.value)} required minLength={8} maxLength={128}/>
                </label>
                <div className={styles.actions}>
                    <Button type="primary" buttonType="submit" disabled={savingPassword}>{t("action.save")}</Button>
                </div>
            </form>

            <section className={styles.panel}>
                <div className={styles.sectionHeader}>
                    <div>
                        <h2>{t("account.ssh_keys")}</h2>
                    </div>
                    <Button
                        type={showAddSshKey ? "secondary" : "primary"}
                        onClick={() => {
                            setExpandedSshKeyId(null);
                            setShowAddSshKey(current => !current);
                        }}
                    >
                        {showAddSshKey ? <FaTimes aria-hidden/> : <FaPlus aria-hidden/>}
                        <span>{showAddSshKey ? t("action.cancel") : t("account.add_ssh_key")}</span>
                    </Button>
                </div>

                {showAddSshKey && (
                    <form className={styles.sshAddForm} onSubmit={handleAddSshKey}>
                        <p className={styles.hint}>{t("account.ssh_keygen_hint")}</p>
                        <code className={styles.command}>ssh-keygen -t ed25519 -C "deine-email@example.com"</code>
                        <label className={styles.field}>
                            <span>{t("account.new_ssh_key")}</span>
                            <textarea value={newSshKey} onChange={event => setNewSshKey(event.target.value)} required rows={3}/>
                        </label>
                        <div className={styles.actions}>
                            <Button type="primary" buttonType="submit" disabled={addingSshKey}>
                                <FaPlus aria-hidden/>
                                <span>{t("action.add")}</span>
                            </Button>
                        </div>
                    </form>
                )}

                <div className={styles.sshList}>
                    {account.sshKeys.length === 0 && <p className={styles.empty}>{t("account.no_ssh_keys")}</p>}
                    {account.sshKeys.map(key => {
                        const info = sshKeyInfo(key.key);
                        return (
                            <div className={styles.sshItem} key={key.id}>
                                <div
                                    className={`${styles.sshCard} ${expandedSshKeyId === key.id ? styles.expanded : ""}`}
                                    onClick={() => {
                                        setShowAddSshKey(false);
                                        setExpandedSshKeyId(current => current === key.id ? null : key.id);
                                    }}
                                    role="button"
                                    tabIndex={0}
                                    onKeyDown={event => {
                                        if (event.key === "Enter" || event.key === " ") {
                                            event.preventDefault();
                                            setShowAddSshKey(false);
                                            setExpandedSshKeyId(current => current === key.id ? null : key.id);
                                        }
                                    }}
                                >
                                    <h3>{info.comment || `${t("account.ssh_key")} ${key.id + 1}`}</h3>
                                    <p>
                                        <span>{info.type}</span>
                                        {info.body && <span>{t("account.ssh_key_fingerprint")}: {info.body.slice(0, 18)}...</span>}
                                        {!info.comment && <span>{t("account.ssh_key_no_comment")}</span>}
                                    </p>
                                </div>
                                {expandedSshKeyId === key.id && (
                                    <div className={styles.sshKeyRow}>
                                        <textarea
                                            value={editedSshKeys[key.id] ?? key.key}
                                            onChange={event => setEditedSshKeys(current => ({
                                                ...current,
                                                [key.id]: event.target.value
                                            }))}
                                            rows={3}
                                        />
                                        <div className={styles.sshActions}>
                                            <Button
                                                type="danger"
                                                onClick={() => handleDeleteSshKey(key.id)}
                                                disabled={savingSshKeyId !== null}
                                            >
                                                <FaTrash aria-hidden/>
                                                <span>{t("action.delete")}</span>
                                            </Button>
                                            <Button
                                                type="primary"
                                                onClick={() => handleUpdateSshKey(key.id)}
                                                disabled={savingSshKeyId !== null}
                                            >
                                                {t("action.save")}
                                            </Button>
                                        </div>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            </section>
        </main>
    );
}

export default AccountRoute;
