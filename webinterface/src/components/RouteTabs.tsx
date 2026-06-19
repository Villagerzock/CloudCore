import { NavLink } from "react-router";
import styles from "./RouteTabs.module.css";

type Tab = {
    label: string;
    to: string;
    end?: boolean;
};

type Props = {
    tabs: Tab[];
};

export function RouteTabs({ tabs }: Props) {
    return (
        <nav className={styles.tabs}>
            {tabs.map(tab => (
                <NavLink
                    key={tab.to}
                    to={tab.to}
                    end={tab.end}
                    className={({ isActive }) =>
                        isActive ? `${styles.tab} ${styles.active}` : styles.tab
                    }
                >
                    {tab.label}
                </NavLink>
            ))}
        </nav>
    );
}
