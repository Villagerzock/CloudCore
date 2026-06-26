import {useParams} from "react-router";
import {
    copyTemplatePath,
    createTemplateFolder,
    deleteTemplatePath,
    downloadTemplateFilePath,
    type FileSystemResponse,
    getServerTemplateFileSystem,
    moveTemplatePath,
    renameTemplatePath,
    uploadTemplateFile
} from "../lib/api.ts";
import {useEffect, useState} from "react";
import FileDisplay from "../components/FileDisplay.tsx";
import FolderDisplay from "../components/FolderDisplay.tsx";
import {usePersistentNavigate} from "../lib/api.ts";
import styles from "./TemplateRoute.module.css";
import {useNodePermissions} from "../hooks/useNodePermissions.ts";
import {useI18n} from "../lib/i18n.ts";

function TemplateRoute(){
    const { template, "*":path } = useParams();
    const navigate = usePersistentNavigate();
    const permissions = useNodePermissions();
    const {t} = useI18n();
    const [ fileSystem, setFileSystem ] = useState<FileSystemResponse | null>(null);
    const [ error, setError ] = useState<string | null>(null);

    const currentPath = path ?? "";

    async function load(){
        if (template) {
            setFileSystem(await getServerTemplateFileSystem(template ?? "",currentPath));
        }
    }

    useEffect(() => {
        load().catch(reason => setError(reason instanceof Error ? reason.message : "Failed to load template path"));
    }, [currentPath, template]);

    function openChild(name: string) {
        if (!permissions.has("TEMPLATES_FILE_READ_DIR")) return;
        if (name === "..") {
            openPath(currentPath.split("/").filter(Boolean).slice(0, -1).join("/"));
            return;
        }
        const nextPath = [currentPath, name]
            .filter(part => part.length > 0)
            .join("/");
        navigate(`/templates/${template}/${nextPath}`);
    }

    function openPath(nextPath: string) {
        if (!permissions.has("TEMPLATES_FILE_READ_DIR")) return;
        navigate(`/templates/${template}/${nextPath}`);
    }

    function childPath(name: string) {
        return [currentPath, name]
            .filter(part => part.length > 0)
            .join("/");
    }

    async function downloadChild(name: string, isFile: boolean) {
        if (!template) return;
        await downloadTemplateFilePath(template, childPath(name), isFile ? name : `${name}.zip`);
    }

    async function deleteChild(name: string) {
        if (!template) return;
        await deleteTemplatePath(template, childPath(name));
        await load();
    }

    async function renameChild(name: string, nextName: string) {
        if (!template) return;
        await renameTemplatePath(template, childPath(name), nextName);
        await load();
    }

    async function copyChild(name: string, destinationFolderPath: string) {
        if (!template) return;
        await copyTemplatePath(template, childPath(name), destinationFolderPath);
        await load();
    }

    async function moveChild(name: string, destinationFolderPath: string) {
        if (!template) return;
        await moveTemplatePath(template, childPath(name), destinationFolderPath);
        await load();
    }

    async function createFolder(name: string) {
        if (!template) return;
        await createTemplateFolder(template, currentPath, name);
        await load();
    }

    async function uploadFiles(uploads: Array<{file: File; relativePath: string}>) {
        if (!template) return;
        for (const upload of uploads) {
            await uploadTemplateFile(template, currentPath, upload.file, upload.relativePath);
        }
        await load();
    }

    if (!fileSystem){
        return error ? <p className={styles.error} role="alert">{error}</p> : <></>;
    }

    const pathParts = currentPath.split("/").filter(Boolean);
    const name = pathParts[pathParts.length - 1] ?? template ?? "template";

    return (
        <main className={styles.page}>
            <nav className={styles.breadcrumbs} aria-label={t("template.path")}>
                <button
                    className={styles.crumb}
                    type="button"
                    onClick={() => navigate("/templates")}
                >
                    templates
                </button>
                <span className={styles.separator}>/</span>
                <button
                    className={`${styles.crumb} ${pathParts.length === 0 ? styles.crumbCurrent : ""}`}
                    type="button"
                    onClick={() => openPath("")}
                >
                    {template}
                </button>
                {pathParts.map((part, index) => {
                    const nextPath = pathParts.slice(0, index + 1).join("/");
                    const current = index === pathParts.length - 1;

                    return (
                        <span key={nextPath} className={styles.crumbGroup}>
                            <span className={styles.separator}>/</span>
                            <button
                                className={`${styles.crumb} ${current ? styles.crumbCurrent : ""}`}
                                type="button"
                                onClick={() => openPath(nextPath)}
                            >
                                {part}
                            </button>
                        </span>
                    );
                })}
            </nav>
            <div className={styles.content}>
                {
                    fileSystem.isFile ?
                        <FileDisplay
                            file={fileSystem}
                            template={template ?? ""}
                            path={currentPath}
                            name={name}
                            canDownload={permissions.has("TEMPLATES_FILE_DOWNLOAD")}
                            canWrite={permissions.has("TEMPLATES_FILE_WRITE")}
                        /> :
                        <FolderDisplay
                            folder={fileSystem}
                            onOpen={openChild}
                            onDownload={downloadChild}
                            onDelete={deleteChild}
                            onRename={renameChild}
                            onCopy={copyChild}
                            onMove={moveChild}
                            onCreateFolder={createFolder}
                            onUpload={uploadFiles}
                            isRoot={currentPath == ""}
                            canOpen={permissions.has("TEMPLATES_FILE_READ_DIR")}
                            canDownload={permissions.has("TEMPLATES_FILE_DOWNLOAD")}
                            canWrite={permissions.has("TEMPLATES_FILE_WRITE")}
                            canCreate={permissions.has("TEMPLATES_FILE_CREATE")}
                        />
                }
            </div>
        </main>
    )
}

export default TemplateRoute;
