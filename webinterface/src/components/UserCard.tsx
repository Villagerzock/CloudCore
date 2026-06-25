import styles from "./UserCard.module.css"
import {usePersistentNavigate} from "../lib/api.ts";

type UserCardProps = {
    title: string;
    email: string;
    role: string;
    id: number
}

function UserCard({title, email, role, id}: UserCardProps){
    const navigate = usePersistentNavigate();

    function goToPage(){
        navigate(`/users/${id}`)
    }

    return (
        <>
            <div className={styles.card} onClick={goToPage}>
                <h2>{title}</h2>
                <p>
                    {email} • {role}
                </p>
            </div>
        </>
    );
}

export default UserCard;