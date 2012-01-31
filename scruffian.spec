%define __jar_repack %{nil}
%define debug_package %{nil}
%define __strip /bin/true
%define __os_install_post   /bin/true
%define __check_files /bin/true
Summary: scruffian
Name: scruffian
Version: 0.1.0
Release: 2
Epoch: 0
BuildArchitectures: noarch
Group: Applications
BuildRoot: %{_tmppath}/%{name}-%{version}-buildroot
License: BSD
Provides: scruffian
Source0: %{name}-%{version}.tar.gz

%description
iPlant Scruffian

%pre
getent group iplant > /dev/null || groupadd -r iplant
getent passwd iplant > /dev/null || useradd -r -g iplant -d /home/iplant -s /bin/bash -c "User for the iPlant services." iplant
exit 0

%prep
%setup -q
mkdir -p $RPM_BUILD_ROOT/etc/init.d/

%build
unset JAVA_OPTS
lein deps
lein uberjar

%install
install -d $RPM_BUILD_ROOT/usr/local/lib/scruffian/
install -d $RPM_BUILD_ROOT/var/run/scruffian/
install -d $RPM_BUILD_ROOT/var/lock/subsys/scruffian/
install -d $RPM_BUILD_ROOT/var/log/scruffian/
install -d $RPM_BUILD_ROOT/etc/scruffian/

install scruffian $RPM_BUILD_ROOT/etc/init.d/
install scruffian-1.0.0-SNAPSHOT-standalone.jar $RPM_BUILD_ROOT/usr/local/lib/scruffian/
install conf/log4j.properties $RPM_BUILD_ROOT/etc/scruffian/
install conf/scruffian.properties $RPM_BUILD_ROOT/etc/scruffian/

%post
/sbin/chkconfig --add scruffian

%preun
if [ $1 -eq 0 ] ; then
	/sbin/service scruffian stop >/dev/null 2>&1
	/sbin/chkconfig --del scruffian
fi

%postun
if [ "$1" -ge "1" ] ; then
	/sbin/service scruffian condrestart >/dev/null 2>&1 || :
fi

%clean
lein clean
rm -r lib/*

%files
%attr(-,iplant,iplant) /usr/local/lib/scruffian/
%attr(-,iplant,iplant) /var/run/scruffian/
%attr(-,iplant,iplant) /var/lock/subsys/scruffian/
%attr(-,iplant,iplant) /var/log/scruffian/
%attr(-,iplant,iplant) /etc/scruffian/

%config %attr(0644,iplant,iplant) /etc/scruffian/log4j.properties
%config %attr(0644,iplant,iplant) /etc/scruffian/scruffian.properties

%attr(0755,root,root) /etc/init.d/scruffian
%attr(0644,iplant,iplant) /usr/local/lib/scruffian/scruffian-1.0.0-SNAPSHOT-standalone.jar


