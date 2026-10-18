"""Offline deployment contract checks. Requires kubectl and PyYAML."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[2]


class ManifestTest(unittest.TestCase):
    def render(self, profile):
        result = subprocess.run(
            ["kubectl", "kustomize", f"k8s/overlays/{profile}"],
            cwd=ROOT, check=True, capture_output=True, text=True,
        )
        return result.stdout, list(yaml.safe_load_all(result.stdout))

    def test_environment_contracts(self):
        for profile in ("dev", "pre"):
            with self.subTest(profile=profile):
                rendered, objects = self.render(profile)
                self.assertTrue(all(o["metadata"]["namespace"] == f"scaffoldops-{profile}" for o in objects))
                self.assertNotIn("Secret", [o["kind"] for o in objects])
                deployment = next(o for o in objects if o["kind"] == "Deployment")
                container = deployment["spec"]["template"]["spec"]["containers"][0]
                env = {e["name"]: e.get("value", e.get("valueFrom")) for e in container["env"]}
                self.assertEqual(profile, env["SPRING_PROFILES_ACTIVE"])
                issuer = f"http://keycloak-{profile}.security.svc.cluster.local:8080/realms/scaffoldops-{profile}"
                self.assertEqual(issuer + "/protocol/openid-connect/certs",
                                 env["SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI"])
                if profile == "pre":
                    self.assertEqual(issuer, env["SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI"])
                    for forbidden in ("scaffoldops-dev", "keycloak-dev", ":develop", "value: dev"):
                        self.assertNotIn(forbidden, rendered)
                self.assertEqual("POSTGRES_GENERATOR_API_PASSWORD",
                                 env["DB_PASSWORD"]["secretKeyRef"]["key"])
                probe = container["startupProbe"]
                self.assertEqual(8080, probe["tcpSocket"]["port"])
                self.assertGreaterEqual(probe["failureThreshold"] * probe["periodSeconds"], 300)
                services = {o["metadata"]["name"]: o for o in objects if o["kind"] == "Service"}
                canonical = services["generator-api-service"]["spec"]
                self.assertEqual({"app": "generator-api"}, canonical["selector"])
                self.assertEqual(80, canonical["ports"][0]["port"])
                self.assertEqual(8080, canonical["ports"][0]["targetPort"])
                if profile == "dev":
                    self.assertEqual({80, 8081}, {p["port"] for p in services["generator-api"]["spec"]["ports"]})
                else:
                    self.assertNotIn("generator-api", services)

    def test_spring_pre_defaults_and_api_path(self):
        resources = ROOT / "src/main/resources"
        pre = yaml.safe_load((resources / "application-pre.yml").read_text())
        jwt = pre["spring"]["security"]["oauth2"]["resourceserver"]["jwt"]
        _, objects = self.render("pre")
        container = next(o for o in objects if o["kind"] == "Deployment")["spec"]["template"]["spec"]["containers"][0]
        env = {e["name"]: e.get("value") for e in container["env"]}
        for prop, variable in (("issuer-uri", "ISSUER_URI"), ("jwk-set-uri", "JWK_SET_URI")):
            name = "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_" + variable
            self.assertEqual("${" + name + ":" + env[name] + "}", jwt[prop])
        application = yaml.safe_load((resources / "application.yml").read_text())
        self.assertEqual("/api/generator/v1", application["spring"]["mvc"]["servlet"]["path"])

    def test_workflow_rendering_and_routing(self):
        workflow = yaml.safe_load((ROOT / ".github/workflows/deploy-k8s.yml").read_text())
        script = next(s["run"] for s in workflow["jobs"]["deploy"]["steps"]
                      if s["name"] == "Render application manifests")
        for branch, profile in (("develop", "dev"), ("main", "pre")):
            pipeline = yaml.safe_load((ROOT / f".github/workflows/{branch}-pipeline.yml").read_text())
            inputs = pipeline["jobs"]["deploy"]["with"]
            self.assertEqual(f"scaffoldops-{profile}", inputs["namespace"])
            self.assertEqual(profile.upper(), inputs["environment_name"])
            self.assertEqual(profile, inputs["spring_profile"])
            with tempfile.TemporaryDirectory() as temp:
                env = dict(os.environ, NAMESPACE=inputs["namespace"], ENVIRONMENT_NAME=inputs["environment_name"],
                           SPRING_PROFILE=profile, IMAGE_NAME="victodomvar/scaffoldops-generator-api",
                           IMAGE_VERSION="0.0.1-test123", RUNNER_TEMP=temp)
                subprocess.run(["bash", "-c", script], cwd=ROOT, env=env, check=True)
                objects = list(yaml.safe_load_all((Path(temp) / "generator-api.yaml").read_text()))
                deployment = next(o for o in objects if o["kind"] == "Deployment")
                self.assertEqual("victodomvar/scaffoldops-generator-api:0.0.1-test123",
                                 deployment["spec"]["template"]["spec"]["containers"][0]["image"])
                env["SPRING_PROFILE"] = "local"
                self.assertNotEqual(0, subprocess.run(["bash", "-c", script], cwd=ROOT, env=env,
                                                     capture_output=True).returncode)
                env["SPRING_PROFILE"] = profile
                env["IMAGE_VERSION"] = "bad\ntag"
                self.assertNotEqual(0, subprocess.run(["bash", "-c", script], cwd=ROOT, env=env,
                                                     capture_output=True).returncode)


if __name__ == "__main__":
    unittest.main()
