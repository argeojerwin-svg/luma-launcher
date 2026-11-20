package dev.luma.remote;
import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteAddressTest {
    private static final String TOKEN="a".repeat(48);
    @Test public void acceptsCompletePrivateProjectorLinks(){
        for(String host:new String[]{"192.168.1.109","10.0.0.2","172.16.1.1","172.31.255.2"})assertNotNull(RemoteAddress.parse("http://"+host+":19767/#"+TOKEN));
    }
    @Test public void rejectsPublicHostsWrongPortsAndIncompleteLinks(){
        for(String url:new String[]{"http://8.8.8.8:19767/#"+TOKEN,"http://172.32.0.1:19767/#"+TOKEN,"http://192.168.1.109/", "https://192.168.1.109:19767/#"+TOKEN,"http://example.com:19767/#"+TOKEN,"http://192.168.999.1:19767/#"+TOKEN,"javascript:alert(1)","http://user@192.168.1.109:19767/#"+TOKEN})assertNull(url,RemoteAddress.parse(url));
    }
    @Test public void restrictsNavigationToPairedPage(){
        var paired=RemoteAddress.parse("http://192.168.1.109:19767/#"+TOKEN);
        assertTrue(RemoteAddress.samePage(paired,paired.toString()));
        assertFalse(RemoteAddress.samePage(paired,"http://192.168.1.110:19767/"));
        assertFalse(RemoteAddress.samePage(paired,"file:///etc/passwd"));
        assertFalse(RemoteAddress.samePage(paired,"http://192.168.1.109:19767/unrelated"));
    }
}
