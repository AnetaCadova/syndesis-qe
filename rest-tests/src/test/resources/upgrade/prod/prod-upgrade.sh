#!/bin/bash
BASE_DIR=$(dirname "$(readlink -f "$0")")
# It is required to be logged in in the cluster in correct namespace.

# This script will:
# - install syndesis from given INSTALL_TAG from INSTALL_DIR
# - executes scenario @prod-upgrade-before
# - creates new image streams with new image versions
# - executes scenario @prod-upgrade-after

# Variables used (define them in "vars" file):
#REGISTRY           - registry for the upgrade image
#SERVER             - server image
#META               - meta image
#UI                 - ui image
#S2I                - s2i image
#OPERATOR           - operator image
#POSTGRES_EXPORTER  - postgres exporter image
#KOMODO_SERVER      - komodo server image
#TAG                - tag which is expected by the operator
#INSTALL_TAG        - git tag from fuse-online-install
#INSTALL_DIR        - path to fuse-online-install repository
#INFRA_ONLY         - flag for upgrading the infra only and skipping maven tasks
#NEXT_BRANCH        - branch for the "upgraded" version

[[ "$(git rev-parse --abbrev-ref HEAD)" =~ ^master$ ]] && echo "You shouldn't run this script from master branch!" && exit 1

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
CURRENT_VERSION="$(echo "${CURRENT_BRANCH}" | cut -c1-3)"

# If the properties aren't defined, source vars file
[[ ! "z${OPERATOR}" == "z" ]] || source "${BASE_DIR}"/vars

git rev-parse --verify "${NEXT_BRANCH}" > /dev/null 2>&1 || (echo "Branch ${NEXT_BRANCH} not found!" && exit 1)

#[[ "${CURRENT_VERSION}" != "$(echo "${INSTALL_TAG}" | cut -c1-3)" ]] && echo "Current branch ${CURRENT_BRANCH} does not match install tag ${INSTALL_TAG}" && exit 1

cd "${INSTALL_DIR}" && git checkout "${INSTALL_TAG}"
bash ./install_ocp.sh --setup
bash ./install_ocp.sh

echo "Waiting for syndesis deployment..."

for pod in "operator" "meta" "server" "ui"; do
    until oc get pods | grep "${pod}" | grep -v deploy | grep -q "1/1"; do echo "syndesis-${pod} not ready yet"; sleep 10; done
done

if [ -z "${INFRA_ONLY}" ]; then
	cd "${BASE_DIR}"/../../../../../.. && ./mvnw clean install -P rest -Dcucumber.options="--tags @prod-upgrade-before"
fi
IMAGES="SERVER META UI S2I OPERATOR POSTGRES_EXPORTER KOMODO_SERVER"

cp -f "${BASE_DIR}"/resources.yml /tmp/resources.yml
for image in ${IMAGES}; do
	echo "Using $image image ${!image}"
	sed -i "s#\\\$$image\\\$#${!image}#g" /tmp/resources.yml
done

echo "Changing tag to ${TAG}"
sed -i "s#\\\$TAG\\\$#${TAG}#g" /tmp/resources.yml

echo "Changing registry to $REGISTRY"
sed -i "s#\\\$REGISTRY\\\$#${REGISTRY}#g" /tmp/resources.yml

echo "Deleting previous resources"
oc delete -f /tmp/resources.yml
sleep 15

echo "Creating new resources"
oc create -f /tmp/resources.yml

sleep 15

echo "Importing images"
for image in "fuse-ignite-ui" "fuse-ignite-meta" "fuse-ignite-s2i" "fuse-ignite-server" "fuse-online-operator"; do
    oc patch is "${image}" -p '{"metadata":{"annotations": {"openshift.io/image.insecureRepository": "true"}}}'
done
oc get is | tail -n +2 | grep -v todo | xargs -L 1 sh -c 'echo $0:$2 && oc import-image $0:$2 && sleep 2'

echo "Waiting for upgrade pod to complete..."

until oc get pods | grep syndesis-upgrade | grep "Completed"; do echo "syndesis-upgrade not completed yet"; sleep 10; done

if [ -z "${INFRA_ONLY}" ]; then
	if [ ! "${CURRENT_BRANCH}" == "${NEXT_BRANCH}" ]; then
		git checkout "${NEXT_BRANCH}"
	fi
	cd "${BASE_DIR}"/../../../../../.. && ./mvnw clean install -P rest -Dcucumber.options="--tags @prod-upgrade-after"
fi
