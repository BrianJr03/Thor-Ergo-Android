# Strict decimal IPv4 / numeric IPv6, with optional IPv6 zone identifier.
# No hostname lookups. POSIX awk: no host Python or GNU-only extensions.
function ipv4(s, a,n,i) {
    if (s !~ /^[0-9.]+$/) return 0
    n=split(s,a,"."); if(n!=4) return 0
    for(i=1;i<=4;i++) {
        if(a[i]=="" || length(a[i])>3 || a[i]+0>255) return 0
        if(length(a[i])>1 && substr(a[i],1,1)=="0") return 0
    }
    return 1
}
function groups(s, a,n,i,c) {
    if(s=="") return 0
    if(substr(s,1,1)==":" || substr(s,length(s),1)==":") return -1
    n=split(s,a,":"); c=0
    for(i=1;i<=n;i++) {
        if(index(a[i],".")) {
            if(i!=n || !ipv4(a[i])) return -1
            c+=2
        } else {
            if(a[i]=="" || length(a[i])>4 || a[i] !~ /^[0-9a-fA-F]+$/) return -1
            c++
        }
    }
    return c
}
function valid(s, p,z,left,right,l,r) {
    if(length(s)>160 || s=="") return 0
    if(substr(s,1,1)=="[" && substr(s,length(s),1)=="]") s=substr(s,2,length(s)-2)
    if(index(s,":")==0) return ipv4(s)
    p=index(s,"%")
    if(p) {
        z=substr(s,p+1); s=substr(s,1,p-1)
        if(z=="" || z !~ /^[a-zA-Z0-9_.-]+$/) return 0
    }
    p=index(s,"::")
    if(p) {
        left=substr(s,1,p-1); right=substr(s,p+2)
        if(index(right,"::") || index(left,".")) return 0
        l=groups(left);r=groups(right)
        return l>=0 && r>=0 && l+r<8
    }
    return groups(s)==8
}
{if(NR!=1 || !valid($0)) bad=1}
END {exit (NR!=1 || bad) ? 1 : 0}
