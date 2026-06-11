package net.villagerzock.cloudcore.core.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NoClassTagRepresenter extends Representer {

    public NoClassTagRepresenter(DumperOptions options) {
        super(options);
    }

    @Override
    protected MappingNode representJavaBean(Set<Property> properties, Object javaBean) {
        List<NodeTuple> value = new ArrayList<>(properties.size());

        MappingNode node = new MappingNode(Tag.MAP, value, DumperOptions.FlowStyle.AUTO);
        this.representedObjects.put(javaBean, node);

        DumperOptions.FlowStyle bestStyle = DumperOptions.FlowStyle.FLOW;

        for (Property property : properties) {
            Object memberValue = property.get(javaBean);

            NodeTuple tuple = this.representJavaBeanProperty(javaBean, property, memberValue, null);

            if (tuple == null) {
                continue;
            }

            if (!((ScalarNode) tuple.getKeyNode()).isPlain()) {
                bestStyle = DumperOptions.FlowStyle.BLOCK;
            }

            Node nodeValue = tuple.getValueNode();

            if (!(nodeValue instanceof ScalarNode) || !((ScalarNode) nodeValue).isPlain()) {
                bestStyle = DumperOptions.FlowStyle.BLOCK;
            }

            value.add(tuple);
        }

        if (this.defaultFlowStyle != DumperOptions.FlowStyle.AUTO) {
            node.setFlowStyle(this.defaultFlowStyle);
        } else {
            node.setFlowStyle(bestStyle);
        }

        return node;
    }

    @Override
    protected NodeTuple representJavaBeanProperty(
            Object javaBean,
            Property property,
            Object propertyValue,
            Tag customTag
    ) {
        NodeTuple tuple = super.representJavaBeanProperty(javaBean, property, propertyValue, null);

        if (tuple == null || propertyValue == null) {
            return tuple;
        }

        Node valueNode = tuple.getValueNode();

        if (
                valueNode instanceof MappingNode
                        && !(propertyValue instanceof Map)
                        && !valueNode.getTag().equals(Tag.SET)
        ) {
            valueNode.setTag(Tag.MAP);
        }

        return tuple;
    }
}
