import './App.module.css'
import Header from "./components/Header.tsx";
import {useState} from "react";
import styles from "./App.module.css"
import BurgerMenu from "./components/BurgerMenu.tsx";
import {FaChartArea, FaGavel, FaLayerGroup, FaServer} from "react-icons/fa";
import BurgerItem from "./components/BurgerItem.tsx";
import {FaChartDiagram, FaGamepad, FaPersonDigging, FaUserGroup} from "react-icons/fa6";
import {Outlet, useRouteError} from "react-router";
import ErrorRoute from "./routes/ErrorRoute.tsx";

function App() {
  const [ is_open, set_is_open ] = useState<boolean>(false);
  const error = useRouteError();
  return (
    <>
      <Header burger_menu_state={is_open} open_burger_menu={set_is_open}/>
      <div className={styles.content}>
        <BurgerMenu is_open={is_open}>
            <BurgerItem route={"/"}>
                <FaChartArea/>
                <p>Dashboard</p>
            </BurgerItem>
            <BurgerItem route={"/proxy"}>
                <FaChartDiagram/>
                <p>Proxy</p>
            </BurgerItem>
            <BurgerItem route={"/servers"}>
                <FaServer/>
                <p>Servers</p>
            </BurgerItem>
            <BurgerItem route={"/templates"}>
                <FaLayerGroup/>
                <p>Templates</p>
            </BurgerItem>
            <BurgerItem route={"/users"}>
                <FaUserGroup/>
                <p>Users</p>
            </BurgerItem>
            <BurgerItem route={"/matchmaking"}>
                <FaGamepad/>
                <p>Matchmaking</p>
            </BurgerItem>
            <BurgerItem route={"/maintenance"}>
                <FaPersonDigging/>
                <p>Maintenance</p>
            </BurgerItem>
            <BurgerItem route={"/bans"}>
                <FaGavel/>
                <p>Banned Players</p>
            </BurgerItem>
        </BurgerMenu>
        <div className={styles.outlet}>
            {error ? <ErrorRoute/> : <Outlet/>}
        </div>
      </div>
    </>
  )
}

export default App
