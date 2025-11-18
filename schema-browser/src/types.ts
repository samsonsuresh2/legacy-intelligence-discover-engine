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

export interface HiddenField {
  name?: string;
  defaultValue?: string;
  expression?: string;
  snippet?: string;
  confidence?: string;
}

export interface SessionDependency {
  key?: string;
  source?: string;
  snippet?: string;
  confidence?: string;
}

export interface FrameDefinition {
  frameName?: string;
  source?: string;
  parentFrameName?: string;
  depth?: number;
  tag?: string;
  confidence?: string;
}

export interface CrossFrameInteraction {
  fromFrame?: string;
  toJsp?: string;
  type?: string;
  snippet?: string;
  confidence?: string;
}

export interface NavigationTarget {
  target?: string;
  sourcePattern?: string;
  snippet?: string;
  confidence?: string;
}

export interface UrlParameterCandidate {
  name?: string;
  source?: string;
  snippet?: string;
  confidence?: string;
}

export interface PageMetadata {
  controllerCandidates?: string[];
  backingBeanCandidates?: string[];
  confidence?: string;
  confidenceScore?: number;
  framesetPage?: boolean;
  notes?: string[];
}

export interface PageSchema {
  pageId?: string;
  title?: string;
  forms?: FormDescriptor[];
  outputs?: OutputSectionDescriptor[];
  frameDefinitions?: FrameDefinition[];
  crossFrameInteractions?: CrossFrameInteraction[];
  navigationTargets?: NavigationTarget[];
  urlParameterCandidates?: UrlParameterCandidate[];
  hiddenFields?: HiddenField[];
  sessionDependencies?: SessionDependency[];
  metadata?: PageMetadata;
}

export interface SummaryPageEntry {
  pageId?: string;
  output?: string;
  forms?: number;
  fields?: number;
  outputs?: number;
  frames?: number;
  frameset?: boolean;
  crossFrameInteractions?: number;
  navigationTargets?: number;
  urlParameters?: number;
  hiddenFields?: number;
  sessionDependencies?: number;
  confidence?: string;
  confidenceScore?: number;
}

export interface SchemaSummary {
  generatedAt?: string;
  pageCount?: number;
  pages?: SummaryPageEntry[];
}
