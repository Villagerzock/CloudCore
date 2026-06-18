import {useEffect, useState} from "react";
import ServerTemplateCard from "../components/ServerTemplateCard.tsx";
import {getServerTemplates, type ServerTemplate} from "../lib/api.ts";
import styles from "./TemplateListRoute.module.css";

function TemplateListRoute(){
    const [templates, setTemplates] = useState<ServerTemplate[]>([]);

    useEffect(() => {
        getServerTemplates().then(setTemplates);
    }, []);

    return (
        <>
            <h1>Templates</h1>
            <div className={styles.list}>
                {templates.map((template) => (
                    <ServerTemplateCard key={template.id} {...template}/>
                ))}
            </div>
        </>
    );
}

export default TemplateListRoute;
