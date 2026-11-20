package dev.luma.remote;

import java.net.URI;

/** Accept only the LAN remote endpoint; never accept public sites or executable URLs. */
final class RemoteAddress {
    static URI parse(String value) {
        try {
            URI uri=new URI(value.trim());
            String host=uri.getHost();
            if(!"http".equalsIgnoreCase(uri.getScheme()) || host==null || uri.getPort()!=19767 || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || !"/".equals(uri.getPath())) return null;
            String[] parts=host.split("\\.");
            if(parts.length!=4) return null;
            int[] ip=new int[4];
            for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return null;ip[i]=Integer.parseInt(parts[i]);if(ip[i]>255)return null;}
            if(!(ip[0]==10 || ip[0]==192 && ip[1]==168 || ip[0]==172 && ip[1]>=16 && ip[1]<=31))return null;
            if(uri.getFragment()==null || !uri.getFragment().matches("[a-f0-9]{48}"))return null;
            return uri;
        } catch(Exception ignored) { return null; }
    }
    static boolean samePage(URI paired,String value) {
        try {URI uri=new URI(value);return "http".equalsIgnoreCase(uri.getScheme()) && paired.getHost().equals(uri.getHost()) && paired.getPort()==uri.getPort() && "/".equals(uri.getPath()) && uri.getRawUserInfo()==null && uri.getRawQuery()==null;}
        catch(Exception ignored){return false;}
    }
}
