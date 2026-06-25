import styles from "./UserListRoute.module.css"
import UserCard from "../components/UserCard.tsx";

function UserListRoute(){
    return (
        <>
            <div className={styles.list}>
                <UserCard title={"Ein Titel"} email={"eine.email@eine.domain"} role={"Owner"}/>
            </div>
        </>
    );
}

export default UserListRoute;