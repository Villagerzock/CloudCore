import {downloadTemplateFile, getTemplateFileContent, saveTemplateFile, type FileResponse} from "../lib/api.ts";
import {Editor} from "@monaco-editor/react";
import {useEffect, useState} from "react";
import styles from "./FileDisplay.module.css";
import Button from "./Button.tsx";
import {useI18n} from "../lib/i18n.ts";

type FileDisplayProps ={
    file: FileResponse;
    template: string;
    path: string;
    name: string;
    canDownload: boolean;
    canWrite: boolean;
}

function FileDisplay({ file, template, path, name, canDownload, canWrite }: FileDisplayProps){
    const {t} = useI18n();
    const [savedContent, setSavedContent] = useState("");
    const [content, setContent] = useState("");
    const [loadingContent, setLoadingContent] = useState(false);
    const [contentError, setContentError] = useState<string | null>(null);
    const [downloadError, setDownloadError] = useState<string | null>(null);
    const [saveError, setSaveError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const changed = content !== savedContent;
    const downloadable = canDownload && (file.downloadUrl !== null || file.binary);

    useEffect(() => {
        setSavedContent("");
        setContent("");
        setContentError(null);
        setSaveError(null);
        setDownloadError(null);

        if (!canDownload) {
            setLoadingContent(false);
            setContentError(t("error.missing_template_download_permission"));
            return;
        }

        if (file.binary || file.tooLarge || file.contentUrl === null) {
            setLoadingContent(false);
            return;
        }

        let cancelled = false;
        setLoadingContent(true);
        getTemplateFileContent(file.contentUrl)
            .then(value => {
                if (cancelled) return;
                setSavedContent(value);
                setContent(value);
            })
            .catch(reason => {
                if (cancelled) return;
                setContentError(reason instanceof Error ? reason.message : "Failed to load file content");
            })
            .finally(() => {
                if (!cancelled) {
                    setLoadingContent(false);
                }
            });

        return () => {
            cancelled = true;
        };
    }, [canDownload, file]);

    function cancel() {
        setContent(savedContent);
    }

    async function save() {
        if (!canWrite) return;
        try {
            setSaving(true);
            setSaveError(null);
            const saved = await saveTemplateFile(template, path, content);
            if (saved.isFile) {
                setSavedContent(content);
            }
        } catch (reason) {
            setSaveError(reason instanceof Error ? reason.message : t("error.save_failed"));
        } finally {
            setSaving(false);
        }
    }

    async function download() {
        if (!canDownload) return;
        try {
            setDownloadError(null);
            if (file.downloadUrl !== null) {
                await downloadTemplateFile(file.downloadUrl, name);
                return;
            }
            throw new Error(t("error.no_download_url"));
        } catch (reason) {
            setDownloadError(reason instanceof Error ? reason.message : t("error.download_failed"));
        }
    }

    if (file.tooLarge) {
        return (
            <div className={styles.fileDisplay}>
                <div className={styles.binaryShell}>
                    <div className={styles.binaryPanel}>
                        <h2>{t("file.too_large")}</h2>
                        <p>{t("file.too_large_detail").replace("{size}", formatBytes(file.sizeBytes))}</p>
                        <Button type="secondary" onClick={download} disabled={!canDownload || file.downloadUrl === null}>
                            {t("action.download")}
                        </Button>
                        {downloadError && <p className={styles.error}>{downloadError}</p>}
                    </div>
                </div>
                <div className={styles.actions}>
                    <Button type="secondary" onClick={cancel}>{t("action.cancel")}</Button>
                    <Button type="primary" disabled>{t("action.save")}</Button>
                </div>
            </div>
        );
    }

    if (file.binary) {
        return (
            <div className={styles.fileDisplay}>
                <div className={styles.binaryShell}>
                    <div className={styles.binaryPanel}>
                        <h2>{t("file.binary")}</h2>
                        <p>{t("file.binary_detail")}</p>
                        <Button type="secondary" onClick={download} disabled={!downloadable || file.downloadUrl === null}>
                            {t("action.download")}
                        </Button>
                        {downloadError && <p className={styles.error}>{downloadError}</p>}
                    </div>
                </div>
                <div className={styles.actions}>
                    <Button type="secondary" onClick={cancel}>{t("action.cancel")}</Button>
                    <Button type="primary" disabled>{t("action.save")}</Button>
                </div>
            </div>
        );
    }

    return (
        <form className={styles.fileDisplay} onSubmit={event => {
            event.preventDefault();
            save();
        }}>
            <div className={styles.editorShell}>
                <div className={styles.editorInner}>
                    <Editor
                        height={"100%"}
                        language={guessLanguage(name)}
                        value={loadingContent ? t("state.loading") : content}
                        onChange={(value)=> setContent(value ?? "")}
                        beforeMount={monaco => {
                            monaco.editor.defineTheme("cloudcore-dark", {
                                base: "vs-dark",
                                inherit: true,
                                rules: [
                                    {token: "comment", foreground: "8f7aa8"},
                                    {token: "keyword", foreground: "c084fc"},
                                    {token: "string", foreground: "d8b4fe"},
                                    {token: "number", foreground: "f0abfc"}
                                ],
                                colors: {
                                    "editor.background": "#18121f",
                                    "editor.foreground": "#f3e8ff",
                                    "editorLineNumber.foreground": "#806a96",
                                    "editorLineNumber.activeForeground": "#d8b4fe",
                                    "editorCursor.foreground": "#c084fc",
                                    "editor.selectionBackground": "#7e22ce66",
                                    "editor.inactiveSelectionBackground": "#581c8766",
                                    "editor.lineHighlightBackground": "#3b235433",
                                    "editorWidget.background": "#21152d",
                                    "editorSuggestWidget.background": "#21152d",
                                    "editorSuggestWidget.border": "#7e22ce"
                                }
                            });
                        }}
                        theme={"cloudcore-dark"}
                        options={{
                            readOnly: !canWrite || loadingContent || contentError !== null,
                            minimap: {enabled: true},
                            fontFamily: "var(--mono)",
                            fontSize: 14,
                            roundedSelection: true,
                            scrollBeyondLastLine: false
                        }}
                    />
                </div>
            </div>
            <div className={styles.actions}>
                <Button type="secondary" onClick={cancel}>{t("action.cancel")}</Button>
                {contentError && <p className={styles.error}>{contentError}</p>}
                {saveError && <p className={styles.error}>{saveError}</p>}
                <Button type="primary" buttonType="submit" disabled={!canWrite || !changed || saving || loadingContent || contentError !== null}>{t("action.save")}</Button>
            </div>
        </form>
    )
}

function formatBytes(bytes: number): string {
    if (bytes < 1024) {
        return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KiB`;
    }
    return `${(bytes / 1024 / 1024).toFixed(1)} MiB`;
}

function guessLanguage(fileName: string): string {
    const extension = fileName.split(".").pop()?.toLowerCase();

    switch (extension) {
        case "ts":
            return "typescript";
        case "tsx":
            return "typescript";
        case "js":
            return "javascript";
        case "jsx":
            return "javascript";

        case "java":
            return "java";
        case "kt":
        case "kts":
            return "kotlin";

        case "c":
            return "c";
        case "h":
            return "cpp";
        case "cpp":
        case "cc":
        case "cxx":
        case "hpp":
        case "hh":
        case "hxx":
            return "cpp";

        case "cs":
            return "csharp";

        case "py":
            return "python";

        case "go":
            return "go";

        case "rs":
            return "rust";

        case "php":
            return "php";

        case "swift":
            return "swift";

        case "rb":
            return "ruby";

        case "scala":
            return "scala";

        case "sql":
            return "sql";

        case "html":
        case "htm":
            return "html";

        case "css":
            return "css";

        case "scss":
            return "scss";

        case "less":
            return "less";

        case "json":
            return "json";

        case "xml":
            return "xml";

        case "yaml":
        case "yml":
            return "yaml";

        case "toml":
            return "toml";

        case "ini":
        case "cfg":
        case "conf":
            return "ini";

        case "md":
        case "markdown":
            return "markdown";

        case "sh":
        case "bash":
            return "shell";

        case "ps1":
            return "powershell";

        case "dockerfile":
            return "dockerfile";

        case "gradle":
            return "groovy";

        case "groovy":
            return "groovy";

        case "properties":
            return "properties";

        case "lua":
            return "lua";

        case "dart":
            return "dart";

        case "r":
            return "r";

        default:
            if (fileName.toLowerCase() === "dockerfile") {
                return "dockerfile";
            }

            return "plaintext";
    }
}

export default FileDisplay;
