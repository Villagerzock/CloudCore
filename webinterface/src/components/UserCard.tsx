import styles from "./UserCard.module.css"

type UserCardProps = {
    title: string;
    email: string;
    role: string;
    id: number;
    onClick?: () => void;
    expanded?: boolean;
}

function UserCard({title, email, role, onClick, expanded = false}: UserCardProps){
    const interactive = onClick !== undefined;

    return (
        <>
            <div
                className={`${styles.card} ${expanded ? styles.expanded : ""} ${!interactive ? styles.readonly : ""}`}
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
                <h2>{title}</h2>
                <p>
                    {email} • {role}
                </p>
            </div>
        </>
    );
}

export default UserCard;
