export interface OptionDescriptor {
  label?: string;
  value?: string;
  selected?: boolean;
}

export interface FieldDescriptor {
  name?: string;
  label?: string;
  type?: string;
  required?: boolean;
  maxLength?: number;
  minLength?: number;
  pattern?: string;
  placeholder?: string;
  defaultValue?: string;
  options?: OptionDescriptor[];
  bindingExpressions?: string[];
  min?: number;
  max?: number;
  javaType?: string;
  constraints?: string[];
  sourceBeanClass?: string;
  sourceBeanProperty?: string;
  notes?: string[];
}

export interface FormDescriptor {
  formId?: string;
  action?: string;
  method?: string;
  backingBeanClassName?: string;
  fields?: FieldDescriptor[];
}

export interface OutputFieldDescriptor {
  name?: string;
  label?: string;
  bindingExpression?: string;
  rawText?: string;
  notes?: string[];
}

export interface OutputSectionDescriptor {
  sectionId?: string;
  type?: string;
  itemVariable?: string;
  itemsExpression?: string;
  fields?: OutputFieldDescriptor[];
  notes?: string[];
}

export interface PageMetadata {
  controllerCandidates?: string[];
  backingBeanCandidates?: string[];
  confidence?: string;
  confidenceScore?: number;
  notes?: string[];
}

export interface PageSchema {
  pageId?: string;
  title?: string;
  forms?: FormDescriptor[];
  outputs?: OutputSectionDescriptor[];
  metadata?: PageMetadata;
}

export interface SummaryPageEntry {
  pageId?: string;
  output?: string;
  forms?: number;
  fields?: number;
  outputs?: number;
  confidence?: string;
  confidenceScore?: number;
}

export interface SchemaSummary {
  generatedAt?: string;
  pageCount?: number;
  pages?: SummaryPageEntry[];
}
