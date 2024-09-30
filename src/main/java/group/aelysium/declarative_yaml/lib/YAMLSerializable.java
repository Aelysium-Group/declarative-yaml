package group.aelysium.declarative_yaml.lib;

import org.spongepowered.configurate.CommentedConfigurationNode;

import java.text.ParseException;

/**
 * A serialization class required in order to parse certain YAML objects.
 */
public abstract class YAMLSerializable {
    /**
     * This constructor nothing other than providing you with the current configuration node you'll need in order to serialize your own object.
     * The caller must take the provided node and parse it according to their data definition.
     * @param node The config node provided by the YAML parser.
     *             This node is not going to be the root node.
     *             If the value to be parsed is located at `root.child.custom-object-key`, then this node will start at `custom-object-key`.
     * @throws Exception If there's any issue serializing the provided data.
     */
    protected YAMLSerializable(CommentedConfigurationNode node) throws Exception {}

    /**
     * Converts the YAML value back into a Node.
     * The node will be used to print to the yaml file if necessary.
     * This method allows you to use custom objects as printable values in the yaml file.
     * @return A YAML Node, most likely to be printed into a yaml file.
     */
    public abstract YAMLNode toNode();
}
