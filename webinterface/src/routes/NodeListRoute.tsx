import {useEffect, useState} from "react";
import {useNavigate} from "react-router";
import Header from "../components/Header.tsx";
import {getNodes, type Node} from "../lib/api.ts";
import styles from "./NodeListRoute.module.css";

function NodeListRoute(){
    const navigate = useNavigate();
    const [nodes, setNodes] = useState<Node[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;

        getNodes()
            .then((result) => {
                if (!cancelled) setNodes(result);
            })
            .catch((reason: unknown) => {
                if (!cancelled) {
                    setError(reason instanceof Error ? reason.message : "Failed to load nodes");
                }
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, []);

    function selectNode(nodeId: number) {
        navigate({pathname: "/", search: `?node=${nodeId}`});
    }

    return (
        <>
            <Header>
                <span className={styles.headerTitle}>Nodes</span>
            </Header>
            <main className={`${styles.page} background`}>
                <section className={styles.panel}>
                    <h1>Select a node</h1>
                    {loading && <p>Loading nodes...</p>}
                    {error && <p role="alert">{error}</p>}
                    {!loading && !error && nodes.length === 0 && (
                        <p>No linked nodes available.</p>
                    )}
                    <div className={styles.list}>
                        {nodes.map((node) => (
                            <button
                                key={node.id}
                                className={styles.node}
                                type="button"
                                onClick={() => selectNode(node.id)}
                            >
                                <strong>{node.name}</strong>
                                <span>Node #{node.id}</span>
                                <code>{node.ipAddress}</code>
                            </button>
                        ))}
                    </div>
                </section>
            </main>
        </>
    );
}

export default NodeListRoute;
