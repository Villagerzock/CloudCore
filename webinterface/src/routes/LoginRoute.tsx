import styles from "./AuthRoute.module.css";
import {RouteTabs} from "../components/RouteTabs.tsx";
import {useState} from "react";
import * as React from "react";
import {login} from "../lib/api.ts";
import Button from "../components/Button.tsx";
import {useI18n} from "../lib/i18n.ts";
import {useToast} from "../components/ToastProvider.tsx";
import {errorMessage} from "../lib/errors.ts";

function LoginRoute(){
    const {t} = useI18n();
    const {showError} = useToast();
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
            localStorage.setItem("auth_username", body.username);
            window.location.assign("/");
        } catch (error) {
            showError(errorMessage(error, "Login failed"));
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
                    type={"password"}
                    value={password}
                    onChange={event => setPassword(event.target.value)}
                    placeholder={t("field.password")}
                />

                <Button type="primary" buttonType="submit" className={styles.submitButton}>
                    {t("auth.login")}
                </Button>
            </form>
            </div>
        </main>
    )
}

export default LoginRoute;
