import {useEffect, useState} from "react";
import type {FormEvent} from "react";
import {
    createMatchmakingConfiguration,
    deleteMatchmakingConfiguration,
    getMatchmakingConfigurations,
    getMatchmakingTemplates,
    updateMatchmakingConfiguration,
    type MatchmakingConfiguration,
    type ServerTemplate
} from "../lib/api.ts";
import Button from "../components/Button.tsx";
import styles from "./MatchmakingRoute.module.css";
import {useI18n} from "../lib/i18n.ts";

const defaultConfiguration: MatchmakingConfiguration = {
    name: "",
    template: "",
    max_amount_of_servers: 1,
    max_players_per_server: 16,
    players_per_team: 1,
    can_rejoin: true,
    split_same_queue: false,
    single_queue_server_on_split: false,
    max_mmv_diff: 100
};

function MatchmakingRoute() {
    const {t} = useI18n();
    const [configurations, setConfigurations] = useState<MatchmakingConfiguration[]>([]);
    const [templates, setTemplates] = useState<ServerTemplate[]>([]);
    const [drafts, setDrafts] = useState<Record<string, MatchmakingConfiguration>>({});
    const [newConfiguration, setNewConfiguration] = useState<MatchmakingConfiguration>(defaultConfiguration);
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState<string | null>(null);

    async function load() {
        const [loadedConfigurations, loadedTemplates] = await Promise.all([
            getMatchmakingConfigurations(),
            getMatchmakingTemplates()
        ]);
        setConfigurations(loadedConfigurations);
        setTemplates(loadedTemplates);
        setDrafts(Object.fromEntries(loadedConfigurations.map(configuration => [configuration.name, configuration])));
        setNewConfiguration({
            ...defaultConfiguration,
            template: loadedTemplates[0]?.name ?? ""
        });
    }

    useEffect(() => {
        load().catch(reason => setError(reason instanceof Error ? reason.message : t("error.load_failed")));
    }, []);

    function updateDraft(name: string, changes: Partial<MatchmakingConfiguration>) {
        setDrafts(current => ({
            ...current,
            [name]: {
                ...current[name],
                ...changes
            }
        }));
    }

    function updateNew(changes: Partial<MatchmakingConfiguration>) {
        setNewConfiguration(current => ({
            ...current,
            ...changes
        }));
    }

    async function saveExisting(event: FormEvent<HTMLFormElement>, name: string) {
        event.preventDefault();
        const draft = drafts[name];
        if (!draft) return;

        try {
            setSaving(name);
            const saved = await updateMatchmakingConfiguration(name, draft);
            setConfigurations(current => current.map(configuration => configuration.name === name ? saved : configuration));
            setDrafts(current => ({...current, [name]: saved}));
            setError(null);
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.save_failed"));
        } finally {
            setSaving(null);
        }
    }

    async function createNew(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        try {
            setSaving("__new");
            const created = await createMatchmakingConfiguration(newConfiguration);
            setConfigurations(current => [...current.filter(configuration => configuration.name !== created.name), created]);
            setDrafts(current => ({...current, [created.name]: created}));
            setNewConfiguration({
                ...defaultConfiguration,
                template: templates[0]?.name ?? ""
            });
            setError(null);
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.save_failed"));
        } finally {
            setSaving(null);
        }
    }

    async function remove(name: string) {
        if (!window.confirm(t("confirm.delete").replace("{name}", name))) return;

        try {
            setSaving(name);
            await deleteMatchmakingConfiguration(name);
            setConfigurations(current => current.filter(configuration => configuration.name !== name));
            setDrafts(current => {
                const next = {...current};
                delete next[name];
                return next;
            });
            setError(null);
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.delete_failed"));
        } finally {
            setSaving(null);
        }
    }

    return (
        <main className={styles.page}>
            <header className={styles.header}>
                <h1>{t("page.matchmaking")}</h1>
            </header>
            {error && <p className={styles.error} role="alert">{error}</p>}
            <section className={styles.grid}>
                <ConfigurationForm
                    configuration={newConfiguration}
                    templates={templates}
                    title={t("matchmaking.new")}
                    submitLabel={t("action.add")}
                    saving={saving === "__new"}
                    onChange={updateNew}
                    onSubmit={createNew}
                />
                {configurations.map(configuration => (
                    <ConfigurationForm
                        key={configuration.name}
                        configuration={drafts[configuration.name] ?? configuration}
                        templates={templates}
                        title={configuration.name}
                        submitLabel={t("action.save")}
                        saving={saving === configuration.name}
                        onChange={changes => updateDraft(configuration.name, changes)}
                        onSubmit={event => saveExisting(event, configuration.name)}
                        onDelete={() => remove(configuration.name)}
                    />
                ))}
            </section>
        </main>
    );
}

type ConfigurationFormProps = {
    configuration: MatchmakingConfiguration;
    templates: ServerTemplate[];
    title: string;
    submitLabel: string;
    saving: boolean;
    onChange: (changes: Partial<MatchmakingConfiguration>) => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
    onDelete?: () => void;
}

function ConfigurationForm({
    configuration,
    templates,
    title,
    submitLabel,
    saving,
    onChange,
    onSubmit,
    onDelete
}: ConfigurationFormProps) {
    const {t} = useI18n();

    return (
        <form className={styles.card} onSubmit={onSubmit}>
            <div className={styles.cardHeader}>
                <h2>{title}</h2>
                {onDelete && (
                    <Button type="danger" onClick={onDelete} disabled={saving}>
                        {t("action.delete")}
                    </Button>
                )}
            </div>
            <label className={styles.field}>
                <span>{t("field.name")}</span>
                <input
                    value={configuration.name}
                    onChange={event => onChange({name: event.target.value})}
                    disabled={onDelete !== undefined}
                    required
                />
            </label>
            <label className={styles.field}>
                <span>{t("field.template")}</span>
                <select
                    value={configuration.template}
                    onChange={event => onChange({template: event.target.value})}
                    required
                >
                    {templates.map(template => (
                        <option key={template.name} value={template.name}>{template.name}</option>
                    ))}
                </select>
            </label>
            <div className={styles.numberGrid}>
                <NumberField label={t("matchmaking.max_servers")} value={configuration.max_amount_of_servers} min={1} onChange={value => onChange({max_amount_of_servers: value})}/>
                <NumberField label={t("matchmaking.max_players")} value={configuration.max_players_per_server} min={1} onChange={value => onChange({max_players_per_server: value})}/>
                <NumberField label={t("matchmaking.players_per_team")} value={configuration.players_per_team} min={1} onChange={value => onChange({players_per_team: value})}/>
                <NumberField label={t("matchmaking.max_mmv_diff")} value={configuration.max_mmv_diff} min={0} onChange={value => onChange({max_mmv_diff: value})}/>
            </div>
            <label className={styles.check}>
                <input type="checkbox" checked={configuration.can_rejoin} onChange={event => onChange({can_rejoin: event.target.checked})}/>
                <span>{t("matchmaking.can_rejoin")}</span>
            </label>
            <label className={styles.check}>
                <input type="checkbox" checked={configuration.split_same_queue} onChange={event => onChange({split_same_queue: event.target.checked})}/>
                <span>{t("matchmaking.split_same_queue")}</span>
            </label>
            <label className={styles.check}>
                <input type="checkbox" checked={configuration.single_queue_server_on_split} onChange={event => onChange({single_queue_server_on_split: event.target.checked})}/>
                <span>{t("matchmaking.single_queue_server_on_split")}</span>
            </label>
            <div className={styles.actions}>
                <Button type="primary" buttonType="submit" disabled={saving || templates.length === 0}>
                    {saving ? t("state.saving") : submitLabel}
                </Button>
            </div>
        </form>
    );
}

function NumberField({
    label,
    value,
    min,
    onChange
}: {
    label: string;
    value: number;
    min: number;
    onChange: (value: number) => void;
}) {
    return (
        <label className={styles.field}>
            <span>{label}</span>
            <input
                type="number"
                min={min}
                value={value}
                onChange={event => onChange(Number(event.target.value))}
                required
            />
        </label>
    );
}

export default MatchmakingRoute;
