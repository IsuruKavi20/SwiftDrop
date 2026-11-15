{{- define "swiftdrop.labels" -}}
app.kubernetes.io/part-of: swiftdrop
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
