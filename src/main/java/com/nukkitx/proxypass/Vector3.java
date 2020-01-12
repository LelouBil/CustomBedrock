package com.nukkitx.proxypass;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.math.vector.Vector3i;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
public class Vector3 {
    public static final Vector3 ZERO = new Vector3(0, 0, 0);
    float x;
    float y;
    float z;

    public static Vector3 from3F(Vector3f other){
        return new Vector3(other.getX(),other.getY(),other.getZ());
    }

    public static Vector3 from3I(Vector3i other) {
        return new Vector3(other.getX(),other.getY(),other.getZ());
    }

    public Vector3 up() {
        return new Vector3(x,y + 1,z);
    }

    @Override
    public String toString() {
        return "{"+ x +
                "," + y +
                "," + z +
                '}';
    }
}
