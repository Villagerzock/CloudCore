import './App.module.css'
import Header from "./components/Header.tsx";
import {useState} from "react";
import styles from "./App.module.css"
import BurgerMenu from "./components/BurgerMenu.tsx";
import {FaBars, FaChartArea, FaGavel, FaLayerGroup, FaServer} from "react-icons/fa";
import BurgerItem from "./components/BurgerItem.tsx";
import {FaChartDiagram, FaGamepad, FaPersonDigging, FaUserGroup} from "react-icons/fa6";
import {Navigate, NavLink, Outlet, useRouteError, useSearchParams} from "react-router";
import ErrorRoute from "./routes/ErrorRoute.tsx";
import ImageButton from "./components/ImageButton.tsx";
import {useNodePermissions} from "./hooks/useNodePermissions.ts";
import {useI18n} from "./lib/i18n.ts";

function App() {
  const [ is_open, set_is_open ] = useState<boolean>(false);
  const [searchParams] = useSearchParams();
  const error = useRouteError();
  const permissions = useNodePermissions();
  const {t} = useI18n();
  const rawNodeId = searchParams.get("node");
  const nodeId = rawNodeId === null ? NaN : Number(rawNodeId);

  function toggleBurgerMenu() {
    set_is_open(!is_open);
  }

  if (!Number.isSafeInteger(nodeId) || nodeId <= 0) {
    return <Navigate to="/nodes" replace/>;
  }

  return (
    <>
      <Header>
        <ImageButton onClick={toggleBurgerMenu}><FaBars size={"2em"}/></ImageButton>
        <NavLink to={"/nodes"}>{t("page.nodes")}</NavLink>
      </Header>
      <div className={`${styles.content} background`}>
        <BurgerMenu is_open={is_open}>
            <BurgerItem route={"/"}>
                <FaChartArea/>
                <p>{t("page.dashboard")}</p>
            </BurgerItem>
            {permissions.has("PROXY_PAGE") && (
                <BurgerItem route={"/proxy"}>
                    <FaChartDiagram/>
                    <p>{t("page.proxy")}</p>
                </BurgerItem>
            )}
            {permissions.has("SERVERS_PAGE") && (
                <BurgerItem route={"/servers"}>
                    <FaServer/>
                    <p>{t("page.servers")}</p>
                </BurgerItem>
            )}
            {permissions.has("TEMPLATES_PAGE") && (
                <BurgerItem route={"/templates"}>
                    <FaLayerGroup/>
                    <p>{t("page.templates")}</p>
                </BurgerItem>
            )}
            {permissions.has("USERS_PAGE") && (
                <BurgerItem route={"/users"}>
                    <FaUserGroup/>
                    <p>{t("page.users")}</p>
                </BurgerItem>
            )}
            {permissions.has("MATCHMAKING_PAGE") && (
                <BurgerItem route={"/matchmaking"}>
                    <FaGamepad/>
                    <p>{t("page.matchmaking")}</p>
                </BurgerItem>
            )}
            {permissions.has("MAINTENANCE_PAGE") && (
                <BurgerItem route={"/maintenance"}>
                    <FaPersonDigging/>
                    <p>{t("page.maintenance")}</p>
                </BurgerItem>
            )}
            {permissions.has("BANNED_PLAYERS_PAGE") && (
                <BurgerItem route={"/bans"}>
                    <FaGavel/>
                    <p>{t("page.banned_players")}</p>
                </BurgerItem>
            )}
        </BurgerMenu>
        <div className={styles.outlet}>
            {error ? <ErrorRoute/> : <Outlet/>}
        </div>
      </div>
    </>
  )
}

export default App
