import styles from "./AuthRoute.module.css";
import {RouteTabs} from "../components/RouteTabs.tsx";
import * as React from "react";
import {useState} from "react";
import {register} from "../lib/api.ts";
import Button from "../components/Button.tsx";
import {useI18n} from "../lib/i18n.ts";

function RegisterRoute(){
    const {t} = useI18n();
    const [username, setUsername] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const form = event.currentTarget;
        const submitButton = form.querySelector<HTMLButtonElement>('button[type="submit"]');
        if (submitButton) submitButton.disabled = true;

        try {
            const body = await register(username, email, password);
            localStorage.setItem("auth_token", body.token);
            localStorage.setItem("auth_username", body.username);
            window.location.assign("/");
        } catch (error) {
            window.alert(error instanceof Error ? error.message : "Registration failed");
        } finally {
            if (submitButton) submitButton.disabled = false;
        }
    }

    return (
        <main className={`${styles.page} background`}>
            <div className={styles.content}>
            <RouteTabs tabs={[{label: t("auth.login"), to: "/login"},{label:t("auth.register"), to: "/register"}]}/>
            <form className={styles.form} onSubmit={handleSubmit}>
                <input
                    type={"text"}
                    value={username}
                    onChange={event => setUsername(event.target.value)}
                    placeholder={t("field.username")}
                />
                <input
                    type={"email"}
                    value={email}
                    onChange={event => setEmail(event.target.value)}
                    placeholder={t("field.email")}
                />
                <input
                    type={"password"}
                    value={password}
                    onChange={event => setPassword(event.target.value)}
                    placeholder={t("field.password")}
                />

                <Button type="primary" buttonType="submit" className={styles.submitButton}>
                    {t("auth.register")}
                </Button>
            </form>
            </div>
        </main>
    )
}

export default RegisterRoute;
