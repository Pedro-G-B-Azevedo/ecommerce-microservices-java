const formatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function Money({ value }: { value: string | number }) {
  return <>{formatter.format(Number(value))}</>
}
