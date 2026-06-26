import styles from "./RoleCard.module.css"
import {FaBars, FaLock} from "react-icons/fa";

type RoleCardProps = {
    title: string;
    id: number;
    locked: boolean;
    onClick?: () => void;
    expanded?: boolean;
}

function RoleCard({title, locked, onClick, expanded = false}: RoleCardProps){
    const interactive = onClick !== undefined || !locked;

    return (
        <>
            <div
                className={`${styles.card} ${locked ? styles.locked : ""} ${expanded ? styles.expanded : ""} ${!interactive ? styles.readonly : ""}`}
                onClick={onClick}
                role={interactive ? "button" : undefined}
                tabIndex={interactive ? 0 : undefined}
                onKeyDown={event => {
                    if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        onClick?.();
                    }
                }}
            >
                {locked ? <FaLock aria-label="Locked"/> : <FaBars aria-label="Drag role"/>}
                <h2>{title}</h2>
            </div>
        </>
    );
}

export default RoleCard;
