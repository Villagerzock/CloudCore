import {useEffect, useState} from "react";
import type {FormEvent} from "react";
import ServerTemplateCard from "../components/ServerTemplateCard.tsx";
import {createTemplate, getServerTemplates, type ServerTemplate} from "../lib/api.ts";
import styles from "./TemplateListRoute.module.css";
import {useNodePermissions} from "../hooks/useNodePermissions.ts";
import {useI18n} from "../lib/i18n.ts";
import Button from "../components/Button.tsx";

function TemplateListRoute(){
    const [templates, setTemplates] = useState<ServerTemplate[]>([]);
    const [name, setName] = useState("");
    const [software, setSoftware] = useState("paper");
    const [version, setVersion] = useState("");
    const [memory, setMemory] = useState("1G");
    const [worldType, setWorldType] = useState("default");
    const [superflatType, setSuperflatType] = useState("default");
    const [seed, setSeed] = useState("");
    const [creating, setCreating] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const permissions = useNodePermissions();
    const {t} = useI18n();

    async function load() {
        setTemplates(await getServerTemplates());
    }

    useEffect(() => {
        load().catch(reason => setError(reason instanceof Error ? reason.message : t("error.load_failed")));
    }, []);

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!permissions.has("TEMPLATES_CREATE")) return;

        try {
            setCreating(true);
            setError(null);
            const template = await createTemplate({
                name: name.trim(),
                server_software: software,
                version: version.trim(),
                memory: memory.trim(),
                world_type: worldType,
                superflat_type: worldType === "superflat" ? superflatType : null,
                seed: seed.trim() || null
            });
            setTemplates(current => [...current.filter(entry => entry.name !== template.name), template]);
            setName("");
            setVersion("");
            setSeed("");
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t("error.create_template_failed"));
        } finally {
            setCreating(false);
        }
    }

    return (
        <>
            <h1>{t("page.templates")}</h1>
            {permissions.has("TEMPLATES_CREATE") && (
                <form className={styles.createForm} onSubmit={submit}>
                    <input
                        value={name}
                        onChange={event => setName(event.target.value)}
                        placeholder={t("field.name")}
                        required
                    />
                    <select value={software} onChange={event => setSoftware(event.target.value)}>
                        <option value="paper">Paper</option>
                        <option value="folia">Folia</option>
                        <option value="vanilla">Vanilla</option>
                        <option value="fabric">Fabric</option>
                    </select>
                    <input
                        value={version}
                        onChange={event => setVersion(event.target.value)}
                        placeholder={t("field.version")}
                        required
                    />
                    <input
                        value={memory}
                        onChange={event => setMemory(event.target.value)}
                        placeholder={t("field.memory")}
                        required
                    />
                    <select value={worldType} onChange={event => setWorldType(event.target.value)}>
                        <option value="default">{t("template.world.default")}</option>
                        <option value="superflat">{t("template.world.superflat")}</option>
                    </select>
                    {worldType === "superflat" && (
                        <select value={superflatType} onChange={event => setSuperflatType(event.target.value)}>
                            <option value="default">{t("template.superflat.default")}</option>
                            <option value="the_void">{t("template.superflat.the_void")}</option>
                            <option value="redstone_ready">{t("template.superflat.redstone_ready")}</option>
                            <option value="water_world">{t("template.superflat.water_world")}</option>
                        </select>
                    )}
                    <input
                        value={seed}
                        onChange={event => setSeed(event.target.value)}
                        placeholder={t("field.seed")}
                    />
                    <Button type="primary" buttonType="submit" disabled={creating}>
                        {creating ? t("state.creating") : t("action.add_template")}
                    </Button>
                    {error && <p className={styles.error}>{error}</p>}
                </form>
            )}
            <div className={styles.list}>
                {templates.map((template) => (
                    <ServerTemplateCard
                        key={template.name}
                        {...template}
                        canOpen={permissions.has("TEMPLATES_FILE_READ_DIR")}
                        canLaunch={permissions.has("SERVER_STATUS")}
                    />
                ))}
            </div>
        </>
    );
}

export default TemplateListRoute;
