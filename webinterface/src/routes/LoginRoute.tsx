import styles from "./AuthRoute.module.css";
import {RouteTabs} from "../components/RouteTabs.tsx";
import {useState} from "react";
import * as React from "react";
import {login} from "../lib/api.ts";

function LoginRoute(){
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const form = event.currentTarget;
        const submitButton = form.querySelector<HTMLButtonElement>('button[type="submit"]');
        if (submitButton) submitButton.disabled = true;

        try {
            const body = await login(username, password);
            localStorage.setItem("auth_token", body.token);
            window.location.assign("/");
        } catch (error) {
            window.alert(error instanceof Error ? error.message : "Login failed");
        } finally {
            if (submitButton) submitButton.disabled = false;
        }
    }

    return (
        <main className={`${styles.page} background`}>
            <div className={styles.content}>
            <RouteTabs tabs={[{label: "Login", to: "/login"},{label:"Register", to: "/register"}]}/>
            <form className={styles.form} onSubmit={handleSubmit}>
                <input
                    type={"text"}
                    value={username}
                    onChange={event => setUsername(event.target.value)}
                    placeholder={"Username"}
                />
                <input
                    type={"password"}
                    value={password}
                    onChange={event => setPassword(event.target.value)}
                    placeholder={"Password"}
                />

                <button type={"submit"}>
                    Login
                </button>
            </form>
            </div>
        </main>
    )
}

export default LoginRoute;
