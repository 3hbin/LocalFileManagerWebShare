#!/bin/sh

##############################################################################
# Gradle start up script for POSIX generated for this project.
##############################################################################

# Attempt to set APP_HOME
app_path=$0
while [ -h "$app_path" ]; do
    ls=$(ls -ld "$app_path")
    link=${ls#*" -> "}
    case $link in
      /*) app_path=$link ;;
      *)  app_path=$(dirname "$app_path")/"$link" ;;
    esac
done

APP_HOME=$(cd "$(dirname "$app_path")" && pwd -P) || exit
APP_NAME="Gradle"
MAX_FD=maximum

warn () { echo "$*"; }
die () { echo; echo "$*"; echo; exit 1; }

cygwin=false
msys=false
darwin=false
nonstop=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true ;;
  MSYS*|MINGW*) msys=true ;;
  NONSTOP*) nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1 ; then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
    fi
fi

if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in
      max*) MAX_FD=$(ulimit -H -n) || warn "Could not query maximum file descriptor limit" ;;
    esac
    case $MAX_FD in
      '' | soft) : ;;
      *) ulimit -n "$MAX_FD" || warn "Could not set maximum file descriptor limit to $MAX_FD" ;;
    esac
fi

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

set -- \
        "-Dorg.gradle.appname=$(basename "$0")" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

eval "set -- $DEFAULT_JVM_OPTS ${JAVA_OPTS-} ${GRADLE_OPTS-}" '"$@"'

exec "$JAVACMD" "$@"
