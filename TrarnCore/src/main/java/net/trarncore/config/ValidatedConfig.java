package net.trarncore.config;

/**
 * Implemented by config classes that need fixing up after being read from disk.
 *
 * <p>{@link JsonConfig} calls {@link #validate()} after every load and before every save, so a
 * hand-edited or partially-written config file can never wedge the mod. Use it to clamp numeric
 * ranges and fill in defaults for fields added since the file was written.
 */
public interface ValidatedConfig {

    void validate();
}
