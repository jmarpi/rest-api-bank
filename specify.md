

## 1 objetivo general
- Construir un microservicio que consulte información de países desde la API pública del Banco Mundial, persista los datos relevantes y los exponga mediante una API REST propia.
- Despues de consumir la api, los datos deben ser almacenados para consumo interno mediante otras apis detelladas en : apis de consumo de usuario
## 2 requerimientos funcionales
- la api primero debe consultar si ese pais existe en su base de datos, si no existe va a consultar a la api del world-bank y almacenara la informacion indicada
- debe existir una api post POST /api/countries/import?code={code}, esta api recibe el codigo de un pais, esta api consume de la api de world bank: GET https://api.worldbank.org/v2/country/{code}?format=json
- validaciones de codigo, no puede recibir codigo vacio, este puede tener 2 o 3 caracteres solo alfabeticos
- Tomar los siguientes campos:
    Código ISO alpha-2.
    Código ISO alpha-3.
    Nombre.
    Capital.
    Región.
    Nivel de ingresos.
    Latitud.
    Longitud.
- Estos campos deben ser mapeados
- si el pais no existe retornar code 201 created
- Si el país ya existe, actualizar sus datos y retornar 200 OK.
- Considerar el código ISO alpha-2 como identificador único de negocio para evitar duplicados.
- para consultar paises almacenados debe ser el siguiente metodo GET /api/countries
    -Retornar todos los países almacenados.
    -Responder 200 OK con un arreglo JSON.
    -Retornar un arreglo vacío cuando no existan registros.
- consulta los paises por id GET /api/countries/{id}
- la api de consulta mundial puede ser configurable y cambiable en el archivo .env.dev
- Todo el sistema se debe desplegar usando docker, con docker compose


## 3 apis de consumo de usuario
- POST /api/countries/import?code={code}
- GET /api/countries 
- GET /api/countries/{id}
## 4 Manejo de errores
- La api rest debe devolver codigos de acuerdo a su codigo de error HTTP
- No omitir ningun error o excepcion
- en caso de no existir la informacion solicitada en la api de bancos mundiales para el mapeo, dejar con datos vacios, y llenar la informacion que existe
## 5 Documentacion entregable
- entregar un swagger de consumo de apis
- generar un Readme con despliegue del sistema y descripcion de apis REST
## respuestas de api de world bank
- consumir una api externa con el method get https://api.worldbank.org/v2/country/{code}?format=json
- respuesta exitosa de cosumo de api:
  {
    "page": 1,
    "pages": 1,
    "per_page": "50",
    "total": 1
  },
  [
    {
      "id": "COL",
      "iso2Code": "CO",
      "name": "Colombia",
      "region": {
        "id": "LCN",
        "iso2code": "ZJ",
        "value": "Latin America & Caribbean "
      },
      "incomeLevel": {
        "id": "UMC",
        "iso2code": "XT",
        "value": "Upper middle income"
      },
      "capitalCity": "Bogota",
      "longitude": "-74.082",
      "latitude": "4.60987"
    }
  ]
]
-serpuesta invalida: 
[
  {
    "message": [
      {
        "id": "120",
        "key": "Invalid value",
        "value": "The provided parameter value is not valid"
      }
    ]
  }
]
