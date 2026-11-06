import type { ReactNode } from 'react'

export function FormField({ name, label, hint, error, required, children }: {
  name: string; label: string; hint?: string; error?: string; required?: boolean; children: ReactNode
}) {
  return <div className="form-field">
    <label htmlFor={name}>{label}{required && <span className="required" aria-hidden="true"> *</span>}</label>
    {children}
    {error ? <p className="field-error" id={`${name}-error`}>{error}</p>
      : hint && <p className="field-hint" id={`${name}-hint`}>{hint}</p>}
  </div>
}
