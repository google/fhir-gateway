# FHIR Access Proxy

[FHIR Access Proxy](../../README.md) is a simple access-control proxy that sits in front of FHIR store and server and controls access to FHIR resources.

## TL;DR

```bash
$ helm repo add opensrp-fhir-access-proxy https://opensrp.github.io/fhir-access-proxy
$ helm install fhir-access-proxy fhir-access-proxy/opensrp-fhir-access-proxy
```

## Introduction

This chart bootstraps  [fhir access proxy](../../README.md) deployment on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager.

## Prerequisites

*   Kubernetes 1.12+
*   Helm 3.1.0

## Installing the Chart

To install the chart with the release name `fhir-access-proxy`:

```shell
helm repo add opensrp-fhir-access-proxy https://opensrp.github.io/fhir-access-proxy &&
helm install fhir-access-proxy fhir-access-proxy/opensrp-fhir-access-proxy
```

## Uninstalling the Chart

To uninstall/delete the `fhir-access-proxy` deployment:

```shell
helm delete fhir-access-proxy
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Parameters

The following table lists the configurable parameters of the fhir access proxy chart and their default values.

## Common Parameters

| Parameter                                    | Description | Default                                                                                                                                                                                                                                                                                  |
|----------------------------------------------|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `replicaCount`                               |             | `1`                                                                                                                                                                                                                                                                                      |
| `image.repository`                           |             | `"opensrp/fhir-access-proxy"`                                                                                                                                                                                                                                                            |
| `image.pullPolicy`                           |             | `"IfNotPresent"`                                                                                                                                                                                                                                                                         |
| `image.tag`                                  |             | `"latest"`                                                                                                                                                                                                                                                                               |
| `imagePullSecrets`                           |             | `[]`                                                                                                                                                                                                                                                                                     |
| `nameOverride`                               |             | `""`                                                                                                                                                                                                                                                                                     |
| `fullnameOverride`                           |             | `""`                                                                                                                                                                                                                                                                                     |
| `serviceAccount.create`                      |             | `true`                                                                                                                                                                                                                                                                                   |
| `serviceAccount.annotations`                 |             | `{}`                                                                                                                                                                                                                                                                                     |
| `serviceAccount.name`                        |             | `""`                                                                                                                                                                                                                                                                                     |
| `podAnnotations`                             |             | `{}`                                                                                                                                                                                                                                                                                     |
| `podSecurityContext`                         |             | `{}`                                                                                                                                                                                                                                                                                     |
| `securityContext`                            |             | `{}`                                                                                                                                                                                                                                                                                     |
| `service.type`                               |             | `"ClusterIP"`                                                                                                                                                                                                                                                                            |
| `service.port`                               |             | `80`                                                                                                                                                                                                                                                                                     |
| `ingress.enabled`                            |             | `false`                                                                                                                                                                                                                                                                                  |
| `ingress.className`                          |             | `""`                                                                                                                                                                                                                                                                                     |
| `ingress.annotations`                        |             | `{}`                                                                                                                                                                                                                                                                                     |
| `ingress.hosts`                              |             | `[{"host": "fhir-access-proxy.local", "paths": [{"path": "/", "pathType": "ImplementationSpecific"}]}]`                                                                                                                                                                                  |
| `ingress.tls`                                |             | `[]`                                                                                                                                                                                                                                                                                     |
| `resources`                                  |             | `{}`                                                                                                                                                                                                                                                                                     |
| `autoscaling.enabled`                        |             | `false`                                                                                                                                                                                                                                                                                  |
| `autoscaling.minReplicas`                    |             | `1`                                                                                                                                                                                                                                                                                      |
| `autoscaling.maxReplicas`                    |             | `100`                                                                                                                                                                                                                                                                                    |
| `autoscaling.targetCPUUtilizationPercentage` |             | `80`                                                                                                                                                                                                                                                                                     |
| `nodeSelector`                               |             | `{}`                                                                                                                                                                                                                                                                                     |
| `tolerations`                                |             | `[]`                                                                                                                                                                                                                                                                                     |
| `affinity`                                   |             | `{}`                                                                                                                                                                                                                                                                                     |
| `recreatePodsWhenConfigMapChange`            |             | `true`                                                                                                                                                                                                                                                                                   |
| `livenessProbe.httpGet.path`                 |             | `"/"`                                                                                                                                                                                                                                                                                    |
| `livenessProbe.httpGet.port`                 |             | `"http"`                                                                                                                                                                                                                                                                                 |
| `readinessProbe.httpGet.path`                |             | `"/"`                                                                                                                                                                                                                                                                                    |
| `readinessProbe.httpGet.port`                |             | `"http"`                                                                                                                                                                                                                                                                                 |
| `initContainers`                             |             | `null`                                                                                                                                                                                                                                                                                   |
| `volumes`                                    |             | `null`                                                                                                                                                                                                                                                                                   |
| `volumeMounts`                               |             | `null`                                                                                                                                                                                                                                                                                   |
| `configMaps`                                 |             | `null`                                                                                                                                                                                                                                                                                   |
| `env`                                        |             | `[{"name": "PROXY_TO", "value": "https://example.com/fhir"}, {"name": "TOKEN_ISSUER", "value": "http://localhost:9080/auth/realms/test-smart"}, {"name": "ACCESS_CHECKER", "value": "list"}, {"name": "ALLOWED_QUERIES_FILE", "value": "resources/hapi_page_url_allowed_queries.json"}]` |
| `pdb.enabled`                                |             | `false`                                                                                                                                                                                                                                                                                  |
| `pdb.minAvailable`                           |             | `""`                                                                                                                                                                                                                                                                                     |
| `pdb.maxUnavailable`                         |             | `1`                                                                                                                                                                                                                                                                                      |
| `vpa.enabled`                                |             | `false`                                                                                                                                                                                                                                                                                  |
| `vpa.updatePolicy.updateMode`                |             | `"Off"`                                                                                                                                                                                                                                                                                  |
| `vpa.resourcePolicy`                         |             | `{}`                                                                                                                                                                                                                                                                                     |

## Overriding Configuration File On Pod Using ConfigMaps

To update config file on the pod with new changes one has to do the following:

(Will be showcasing an example of overriding the [hapi\_page\_url\_allowed\_queries.json](../../resources/hapi\_page\_url\_allowed\_queries.json) file).

1.  Create a configmap entry, like below:
    *   The `.Values.configMaps.name` should be unique per entry.
    *   Ensure indentation of the content is okay.
    ```yaml
    configMaps:
      - name: hapi_page_url_allowed_queries.json
        contents: |
          {
            "entries": [
              {
                "path": "",
                "queryParams": {
                  "_getpages": "ANY_VALUE"
                },
                "allowExtraParams": true,
                "allParamsRequired": true,
                "newConfigToAdd": false
              }
            ]
          }
    ```
2.  Create a configmap volume type:
    *   The name of the configMap resemble the ConfigMap manifest metadata.name i.e. `fhir-access-proxy` but we obtain the generated name from the function `'{{ include "fhir-access-proxy.fullname" . }}'` using tpl function.
    ```yaml
    volumes:
        - name: hapi-page-url-allowed-queries
          configMap:
             name: '{{ include "fhir-access-proxy.fullname" . }}'
    ```
3.  Mount the Configmap volume:
    *   mountPath is the location of the file in the pod.
    *   name is the name of the volume in point 2 above.
    *   subPath is the name of the configMap used in point 1 above.
    ```yaml
    volumeMounts:
      - mountPath: /app/resources/hapi_page_url_allowed_queries.json
        name: hapi-page-url-allowed-queries
        subPath: hapi_page_url_allowed_queries.json
    ```
4.  Deploy.
    *   To confirm it has picked the new changes you can check the file by:
    ```shell
    kubectl exec -it <pod-name>  -- cat resources/hapi_page_url_allowed_queries.json
    ```

Done.
