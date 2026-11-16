package android.content;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public final class ContentResolver {
    public final Map<String, String> values = new HashMap<>();
    public final List<String> writes = new ArrayList<>();
    public int failureCall = -1;
    public boolean throwInstead;
    public int calls;
}
